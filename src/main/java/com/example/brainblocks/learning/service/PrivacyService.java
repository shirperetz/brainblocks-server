package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnContact;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnContactRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-14 (Shir, before going public: privacy policy, parent consent, accessibility, deletion):
 * a child / teacher asks to delete the account (with the password), the admin approves, and then
 * everything about that account is erased - every table that points at it, then the account itself.
 * Also the messages to the site's operator (the contact form on the privacy / accessibility pages).
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger("brainblocks.privacy");
    /** Columns that hold the id of the account the row belongs to. */
    private static final Set<String> OWNER_COLUMNS = Set.of("profile_id", "child_id", "teacher_id");
    public static final Set<String> KINDS = Set.of("privacy", "deletion", "accessibility", "problem", "other");

    private final LearnerProfileRepository profiles;
    private final LearnContactRepository contacts;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final AccountService accounts;
    private PhoneAlerts alerts;

    public PrivacyService(LearnerProfileRepository profiles, LearnContactRepository contacts, JdbcTemplate jdbc,
                          DataSource dataSource, AccountService accounts) {
        this.profiles = profiles;
        this.contacts = contacts;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.accounts = accounts;
    }

    @Autowired(required = false)
    void setAlerts(PhoneAlerts alerts) {
        this.alerts = alerts;
    }

    // ---- deletion -------------------------------------------------------------------------------

    public LocalDateTime requestDeletion(Long id, String password) {
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (p.isAdmin()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "את חשבון המנהלת אי אפשר למחוק מכאן");
        if (!accounts.passwordOk(id, password)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הסיסמה לא נכונה");
        if (p.getDeletionRequestedAt() == null) {
            p.setDeletionRequestedAt(LocalDateTime.now());
            profiles.save(p);
            if (alerts != null) {
                alerts.send("deletion-" + id, "בקשה למחוק חשבון: " + p.getName() + " (@" + p.getUsername() + ") - מחכה לאישור שלך בניהול");
            }
        }
        return p.getDeletionRequestedAt();
    }

    public void cancelDeletion(Long id) {
        profiles.findById(id).ifPresent(p -> {
            p.setDeletionRequestedAt(null);
            profiles.save(p);
        });
    }

    public LocalDateTime deletionRequestedAt(Long id) {
        return profiles.findById(id).map(LearnerProfile::getDeletionRequestedAt).orElse(null);
    }

    public List<Map<String, Object>> deletionRequests() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnerProfile p : profiles.findAll()) {
            if (p.getDeletionRequestedAt() == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("username", p.getUsername());
            m.put("teacher", p.isTeacher());
            m.put("requestedAt", p.getDeletionRequestedAt());
            out.add(m);
        }
        out.sort((a, b) -> ((LocalDateTime) a.get("requestedAt")).compareTo((LocalDateTime) b.get("requestedAt")));
        return out;
    }

    /** Erases the account and every row that belongs to it. Returns how many rows went, per table. */
    @Transactional
    public Map<String, Integer> erase(Long id) {
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (p.isAdmin()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "את חשבון המנהלת אי אפשר למחוק");
        Integer classes = jdbc.queryForObject("select count(*) from learn_class where teacher_id = ?", Integer.class, id);
        if (classes != null && classes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "למורה הזאת יש " + classes + " כיתות - קודם להעביר אותן למורה אחרת או למחוק אותן (בעץ \"מורים וכיתות\")");
        }
        Map<String, Integer> gone = new LinkedHashMap<>();
        for (String[] tc : ownerColumns()) {
            int n = jdbc.update("delete from " + tc[0] + " where " + tc[1] + " = ?", id);
            if (n > 0) gone.merge(tc[0], n, Integer::sum);
        }
        profiles.deleteById(id);
        gone.put("learn_profile", 1);
        log.info("erased account {} ({} tables)", id, gone.size());
        return gone;
    }

    /** Every (table, column) that is an owner column - read from the database itself, so new tables are covered. */
    private List<String[]> ownerColumns() {
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(c.getCatalog(), c.getSchema(), "%", "%")) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME").toLowerCase();
                    String column = rs.getString("COLUMN_NAME").toLowerCase();
                    if (!OWNER_COLUMNS.contains(column) || table.equals("learn_profile") || !table.matches("[a-z0-9_]+")) continue;
                    if (seen.add(table + "." + column)) out.add(new String[] {table, column});
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not read the tables", e);
        }
        return out;
    }

    // ---- messages to the operator ---------------------------------------------------------------

    public void contact(String kind, String name, String reply, String text, Long profileId) {
        String k = kind == null || !KINDS.contains(kind) ? "other" : kind;
        String t = text == null ? "" : text.trim();
        if (t.length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "מה תרצו לכתוב?");
        contacts.save(new LearnContact(k, cut(name, 60), cut(reply, 120), cut(t, 2000), profileId));
        if (alerts != null) alerts.send("contact", "הודעה חדשה באתר (" + label(k) + "): " + cut(t, 80));
    }

    public List<Map<String, Object>> messages() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnContact c : contacts.findTop200ByOrderByIdDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("kind", c.getKind());
            m.put("kindLabel", label(c.getKind()));
            m.put("name", c.getName());
            m.put("reply", c.getReply());
            m.put("text", c.getText());
            m.put("at", c.getAt());
            m.put("done", c.isDone());
            m.put("account", c.getProfileId() == null ? null
                    : profiles.findById(c.getProfileId()).map(LearnerProfile::getUsername).orElse("(נמחק)"));
            out.add(m);
        }
        return out;
    }

    public void markDone(Long id, boolean done) {
        contacts.findById(id).ifPresent(c -> {
            c.setDone(done);
            contacts.save(c);
        });
    }

    static String label(String kind) {
        return switch (kind) {
            case "privacy" -> "פרטיות";
            case "deletion" -> "מחיקת מידע";
            case "accessibility" -> "נגישות";
            case "problem" -> "תקלה";
            default -> "אחר";
        };
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
