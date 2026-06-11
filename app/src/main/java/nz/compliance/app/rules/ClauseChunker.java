package nz.compliance.app.rules;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits document text into clauses on numbered headings like "3.1", "3.1.2". */
@Component
public class ClauseChunker {

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*(\\d+(?:\\.\\d+)+)\\s+");

    public List<Clause> chunk(String text) {
        Matcher m = HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> cites = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            cites.add(m.group(1));
        }
        List<Clause> clauses = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            clauses.add(new Clause(cites.get(i), text.substring(starts.get(i), end).trim()));
        }
        return clauses;
    }
}
