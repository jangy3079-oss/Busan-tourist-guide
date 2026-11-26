package kr.busan.safetrip.emergency.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import kr.busan.safetrip.common.api.EmType;

/**
 * [긴급 담당] COMP-E10 / COMP-E12 KbDocumentRepository
 * 요구사항: FR-E016, FR-E022, DATA-E09, ADR-E08 (#111 #114 #115)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * kb_document 표를 JdbcTemplate으로 읽고 쓴다(TEXT[], JSONB, pgvector 1024차원 정확 검색).
 */
@Repository
public class KbDocumentRepository {
    public record KeyLang(String docKey, String lang) {}

    public record HashState(String contentHash, boolean hasEmbedding) {}

    public record KbDoc(
            String docKey,
            String lang,
            String title,
            List<String> types,
            List<String> groups,
            String status,
            LocalDate checked,
            List<String> sources,
            List<String> steps,
            String body,
            String contentHash,
            boolean hasEmbedding) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public KbDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 기존 행의 (doc_key, lang)별 content_hash와 임베딩 존재 여부를 조회한다. */
    public Map<KeyLang, HashState> findAllHashStates() {
        Map<KeyLang, HashState> result = new HashMap<>();
        jdbc.query(
                "SELECT doc_key, lang, content_hash, (embedding IS NOT NULL) AS has_embedding FROM kb_document",
                rs -> {
                    result.put(
                            new KeyLang(rs.getString("doc_key"), rs.getString("lang")),
                            new HashState(rs.getString("content_hash"), rs.getBoolean("has_embedding")));
                });
        return result;
    }

    /** 새 행 또는 본문/임베딩 변경 행을 upsert한다(embeddingLiteral이 null이면 embedding=NULL). */
    public void upsertWithEmbedding(KbDoc doc, String embeddingLiteral, Instant now) {
        String sql = """
                INSERT INTO kb_document (
                    doc_key, lang, title, types, groups, status, checked,
                    sources, steps, body, content_hash, embedding, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, CAST(? AS vector), ?
                )
                ON CONFLICT (doc_key, lang) DO UPDATE SET
                    title = EXCLUDED.title,
                    types = EXCLUDED.types,
                    groups = EXCLUDED.groups,
                    status = EXCLUDED.status,
                    checked = EXCLUDED.checked,
                    sources = EXCLUDED.sources,
                    steps = EXCLUDED.steps,
                    body = EXCLUDED.body,
                    content_hash = EXCLUDED.content_hash,
                    embedding = EXCLUDED.embedding,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            bindCommon(con, ps, doc);
            if (embeddingLiteral == null) {
                ps.setNull(12, Types.VARCHAR);
            } else {
                ps.setString(12, embeddingLiteral);
            }
            ps.setTimestamp(13, Timestamp.from(now));
            return ps;
        });
    }

    /** content_hash가 같고 이미 임베딩이 있는 행은 기존 embedding을 유지한 채 메타데이터만 갱신한다. */
    public void upsertKeepingEmbedding(KbDoc doc, Instant now) {
        String sql = """
                INSERT INTO kb_document (
                    doc_key, lang, title, types, groups, status, checked,
                    sources, steps, body, content_hash, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?
                )
                ON CONFLICT (doc_key, lang) DO UPDATE SET
                    title = EXCLUDED.title,
                    types = EXCLUDED.types,
                    groups = EXCLUDED.groups,
                    status = EXCLUDED.status,
                    checked = EXCLUDED.checked,
                    sources = EXCLUDED.sources,
                    steps = EXCLUDED.steps,
                    body = EXCLUDED.body,
                    content_hash = EXCLUDED.content_hash,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            bindCommon(con, ps, doc);
            ps.setTimestamp(12, Timestamp.from(now));
            return ps;
        });
    }

    /** 파일 목록에서 사라진 문서 키를 삭제한다. */
    public int deleteKeysNotIn(Collection<String> activeDocKeys) {
        if (activeDocKeys == null || activeDocKeys.isEmpty()) {
            return jdbc.update("DELETE FROM kb_document");
        }
        String[] keys = activeDocKeys.toArray(String[]::new);
        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("DELETE FROM kb_document WHERE NOT (doc_key = ANY(?))");
            ps.setArray(1, con.createArrayOf("text", keys));
            return ps;
        });
    }

    /** 언어·유형 일치 후보를 조회한다. queryVectorLiteral이 있으면 pgvector 코사인 거리(<=>) 순으로 정렬한다(ADR-E08). */
    public List<KbDoc> findCandidates(String lang, EmType type, String queryVectorLiteral) {
        if (queryVectorLiteral != null && !queryVectorLiteral.isBlank()) {
            String sql = """
                    SELECT doc_key, lang, title, types, groups, status, checked,
                           sources, steps, body, content_hash, (embedding IS NOT NULL) AS has_embedding
                    FROM kb_document
                    WHERE lang = ? AND ? = ANY(types)
                    ORDER BY CASE WHEN embedding IS NULL THEN 1 ELSE 0 END,
                             embedding <=> CAST(? AS vector) ASC,
                             doc_key ASC
                    """;
            return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), lang, type.name(), queryVectorLiteral);
        }
        String sql = """
                SELECT doc_key, lang, title, types, groups, status, checked,
                       sources, steps, body, content_hash, (embedding IS NOT NULL) AS has_embedding
                FROM kb_document
                WHERE lang = ? AND ? = ANY(types)
                ORDER BY doc_key ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), lang, type.name());
    }

    /** 특정 문서 키·언어 1건을 조회한다(연락처 문서 contact-* 강제 포함용). */
    public Optional<KbDoc> findByKeyAndLang(String docKey, String lang) {
        String sql = """
                SELECT doc_key, lang, title, types, groups, status, checked,
                       sources, steps, body, content_hash, (embedding IS NOT NULL) AS has_embedding
                FROM kb_document
                WHERE doc_key = ? AND lang = ?
                """;
        List<KbDoc> list = jdbc.query(sql, (rs, rowNum) -> mapRow(rs), docKey, lang);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM kb_document", Integer.class);
        return count == null ? 0 : count;
    }

    private static void bindCommon(java.sql.Connection con, PreparedStatement ps, KbDoc doc) throws SQLException {
        ps.setString(1, doc.docKey());
        ps.setString(2, doc.lang());
        ps.setString(3, doc.title());
        ps.setArray(4, con.createArrayOf("text", doc.types().toArray(String[]::new)));
        ps.setArray(5, con.createArrayOf("text", doc.groups().toArray(String[]::new)));
        if (doc.status() == null) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, doc.status());
        }
        if (doc.checked() == null) {
            ps.setNull(7, Types.DATE);
        } else {
            ps.setDate(7, Date.valueOf(doc.checked()));
        }
        ps.setString(8, toJson(doc.sources()));
        ps.setString(9, toJson(doc.steps()));
        ps.setString(10, doc.body());
        ps.setString(11, doc.contentHash());
    }

    private static KbDoc mapRow(ResultSet rs) throws SQLException {
        Date checkedDate = rs.getDate("checked");
        return new KbDoc(
                rs.getString("doc_key"),
                rs.getString("lang"),
                rs.getString("title"),
                readTextArray(rs.getArray("types")),
                readTextArray(rs.getArray("groups")),
                rs.getString("status"),
                checkedDate == null ? null : checkedDate.toLocalDate(),
                fromJsonList(rs.getString("sources")),
                fromJsonList(rs.getString("steps")),
                rs.getString("body"),
                rs.getString("content_hash"),
                rs.getBoolean("has_embedding"));
    }

    private static List<String> readTextArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] strs) {
            return List.of(strs);
        }
        if (raw instanceof Object[] objs) {
            return Arrays.stream(objs).map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String toJson(List<String> list) {
        try {
            return JSON.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static List<String> fromJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = JSON.readValue(raw, STRING_LIST);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
