package nz.compliance.app.rules;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

interface AiRuleExtractor {

    @SystemMessage("""
        You convert New Zealand building-code (NZBC C/AS2) means-of-escape provisions into
        structured compliance rules. Use ONLY these parameters:
          OPEN_PATH_LENGTH (metres), DEAD_END_LENGTH (metres), OCCUPANT_LOAD,
          EXIT_COUNT, EXIT_WIDTH (millimetres).
        Use ONLY these comparators: LTE, GTE, EQ.
        If a provision does not map cleanly to one parameter, omit it.
        For every rule include the verbatim sourceQuote it came from, a citation, and a
        confidence in [0,1]. Do NOT invent numeric thresholds you cannot see in the text.""")
    ExtractionOutput extract(@UserMessage String clauseText);
}
