package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/** Real vision extractor (active under the "claude" profile). Lazily built so the app boots without a key. */
@Component
@Profile("claude")
public class ClaudeVisionPlanExtractor implements VisionPlanExtractor {

    private static final String SYSTEM = """
        You extract a building floor plan from an image for a fire-egress tool.
        Return ONLY JSON (no prose, no markdown fences) of the form:
        {"rooms":[{"label":string,"occupancyTypeGuess":"WB"|"CA"|"","polygonPx":[{"x":number,"y":number}],"confidence":number}],
         "doors":[{"positionPx":[{"x":number,"y":number},{"x":number,"y":number}],"connectsRoomLabels":[string],
                   "exitGuess":boolean,"clearWidthMmGuess":number|null,"confidence":number}],
         "scaleGuess":{"metresPerPixel":number,"source":string,"confidence":number}|null,
         "warnings":[string]}
        Coordinates are IMAGE PIXELS, origin top-left. A door's positionPx is its 2-point opening.
        Mark exitGuess=true only if a door clearly discharges outside / to a final exit.
        If you cannot read a scale bar or dimension, set scaleGuess to null. Never invent numbers.""";

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile ChatModel model;

    public ClaudeVisionPlanExtractor(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public PlanExtraction extract(RenderedImage image) {
        String base64 = Base64.getEncoder().encodeToString(image.pngBytes());
        UserMessage user = UserMessage.from(
                ImageContent.from(base64, "image/png"),
                TextContent.from("Extract the floor plan. Image is "
                        + image.widthPx() + "x" + image.heightPx() + " px."));
        String json = model().chat(SystemMessage.from(SYSTEM), user).aiMessage().text();
        return parse(json);
    }

    /** Parse the model's text into a PlanExtraction; malformed output degrades to a warning-only result. */
    PlanExtraction parse(String raw) {
        try {
            return mapper.readValue(stripFences(raw), PlanExtraction.class);
        } catch (Exception e) {
            return new PlanExtraction(List.of(), List.of(), null,
                    List.of("Could not parse the vision output — trace over the backdrop instead."));
        }
    }

    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            // whitespace (incl. none/space/newline) after the opening fence and before the closing one,
            // so single-line and no-trailing-newline fenced blocks are stripped too.
            t = t.replaceFirst("(?s)^```[a-zA-Z]*\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        return t;
    }

    private ChatModel model() {
        ChatModel local = model;
        if (local == null) {
            synchronized (this) {
                local = model;
                if (local == null) {
                    model = local = AnthropicChatModel.builder()
                            .apiKey(apiKey).modelName("claude-sonnet-4-6").maxTokens(4096).build();
                }
            }
        }
        return local;
    }
}
