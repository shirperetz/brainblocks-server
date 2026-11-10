package com.example.brainblocks.learning.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * "לא הבנתי" - a child-friendly explanation of ONE question from a local model (Ollama on this
 * computer; nothing leaves it). The child never types to the model: it only sees the question the
 * game made. Every reply passes {@link HelpText} (short, Hebrew, never the answer); a reply that
 * fails is asked once more, and if that fails too - or the model is off - the game's own hint is
 * used. Configure with brainblocks.ai.* (enabled / url / model).
 */
@Service
public class HelpService {

    private static final Logger log = LoggerFactory.getLogger(HelpService.class);
    private static final String[] GRADE_NAMES = {"כיתה א׳", "כיתה ב׳", "כיתה ג׳", "כיתה ד׳", "כיתה ה׳", "כיתה ו׳"};

    private final boolean enabled;
    private final String url;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();

    public HelpService(@Value("${brainblocks.ai.enabled:true}") boolean enabled,
                       @Value("${brainblocks.ai.url:http://localhost:11434}") String url,
                       @Value("${brainblocks.ai.model:aya-expanse:8b}") String model) {
        this.enabled = enabled;
        this.url = url.replaceAll("/+$", "");
        this.model = model;
    }

    public record Help(String text, String source) {}

    public Help explain(String question, String answer, String chosen, String grade, boolean again, String fallback) {
        String q = trim(question, 200);
        if (enabled && !q.isBlank()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                String reply = HelpText.clean(ask(systemPrompt(grade, attempt > 0), userPrompt(q, trim(chosen, 20), again)));
                if (HelpText.safe(reply, answer)) return new Help(reply, "ai");
                log.info("help: reply rejected (attempt {})", attempt + 1);
            }
        }
        String hint = trim(fallback, 240);
        return new Help(hint.isBlank() ? "בואו נקרא את השאלה שוב לאט, ונחשוב צעד אחרי צעד." : hint, "hint");
    }

    static String systemPrompt(String grade, boolean strict) {
        return "את מורה סבלנית לחשבון לילדים ב" + gradeName(grade) + ". ילדים לא הבינו שאלה. "
                + "הסבירי בעברית פשוטה מאוד, תמיד בלשון רבים (אתם, נסו, חשבו), בשניים או שלושה משפטים קצרים, עד 40 מילים. "
                + "תני דרך לחשוב על השאלה, או דוגמה דומה עם מספרים אחרים. "
                + "במספרים גדולים, הציעו לפרק לפי מקומות: קודם האחדות, אחר כך העשרות, אחר כך המאות. "
                + "אסור לכתוב את התשובה הסופית לשאלה, ואסור לעשות את החישוב עד הסוף - הילדים צריכים להגיע אליה לבד. "
                + (strict ? "חשוב מאוד: אל תכתבי שום תוצאה של השאלה עצמה, רק את הדרך. " : "")
                + "דברי רק על השאלה הזאת. בלי רשימות, בלי קישורים, בלי אנגלית, בלי שאלות אישיות.";
    }

    static String userPrompt(String question, String chosen, boolean again) {
        StringBuilder u = new StringBuilder("השאלה: ").append(question);
        if (!chosen.isBlank()) u.append("\nהילדים ענו ").append(chosen).append(", וזה לא נכון.");
        if (again) u.append("\nההסבר הקודם לא עזר. הסבירי בדרך אחרת לגמרי, עם משהו מהחיים (ממתקים, כדורים, צעדים).");
        return u.toString();
    }

    private String ask(String system, String user) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model, "stream", false, "keep_alive", "30m",
                    "options", Map.of("temperature", 0.3, "num_predict", 140),
                    "messages", List.of(Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/api/chat"))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            JsonNode n = json.readTree(res.body()).path("message").path("content");
            return n.isTextual() ? n.asText() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.info("help: model not reachable ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String gradeName(String grade) {
        if (grade != null && grade.matches("G[1-6]")) return GRADE_NAMES[grade.charAt(1) - '1'];
        return "בית הספר היסודי";
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
