package nz.compliance.app.rules;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!test")   // tests provide a stub instead
public class LangChain4jRuleExtractor implements RuleExtractor {

    private final String apiKey;
    private volatile AiRuleExtractor ai;

    public LangChain4jRuleExtractor(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<RuleCandidate> extract(String clauseText) {
        return ai().extract(clauseText).rules();
    }

    /**
     * Builds the Claude-backed AiService lazily, on first use, so the app — and every
     * full-context integration test that does not actually exercise extraction — boots
     * without an ANTHROPIC_API_KEY. The key is only needed when extraction runs.
     */
    private AiRuleExtractor ai() {
        AiRuleExtractor local = ai;
        if (local == null) {
            synchronized (this) {
                local = ai;
                if (local == null) {
                    ChatModel model = AnthropicChatModel.builder()
                            .apiKey(apiKey)
                            .modelName("claude-sonnet-4-6")
                            .build();
                    ai = local = AiServices.create(AiRuleExtractor.class, model);
                }
            }
        }
        return local;
    }
}
