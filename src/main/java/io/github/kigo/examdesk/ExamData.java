package io.github.kigo.examdesk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 1回分の試験。XML形式は旧バージョンと互換で、Exam 要素に Phase / SavedAt / ElapsedSeconds を追加している
 * （旧バージョンは追加要素を無視するので、新しいファイルも旧版で開ける）。
 */
public record ExamData(String name, Phase phase, LocalDateTime savedAt, long elapsedSeconds, List<Question> questions) {

    public enum Phase { ANSWERING, CHECKING, GRADED }

    public record Question(String answer, String correct, boolean isCorrect) {
    }

    public static ExamData create(String name, int count) {
        return new ExamData(name, Phase.ANSWERING, LocalDateTime.now(), 0,
                Collections.nCopies(count, new Question("", "", false)));
    }

    public int correctCount() {
        return (int) questions.stream().filter(Question::isCorrect).count();
    }

    public double rate() {
        return questions.isEmpty() ? 0 : (double) correctCount() / questions.size();
    }

    public static ExamData read(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        Element root = doc.getDocumentElement();
        if (!"ExamResults".equals(root.getTagName())) {
            throw new IOException("試験ファイルではありません: " + file.getName());
        }

        Element exam = (Element) root.getElementsByTagName("Exam").item(0);
        String name = exam == null ? "" : text(exam, "Name");
        String phase = exam == null ? "" : text(exam, "Phase");
        String savedAt = exam == null ? "" : text(exam, "SavedAt");
        String elapsed = exam == null ? "" : text(exam, "ElapsedSeconds");

        List<Question> questions = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("Question");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element q = (Element) nodes.item(i);
            questions.add(new Question(text(q, "StudentAnswer"), text(q, "CorrectAnswer"),
                    Boolean.parseBoolean(text(q, "IsCorrect"))));
        }

        return new ExamData(
                name.isBlank() ? file.getName().replaceFirst("\\.xml$", "") : name,
                phase.isEmpty() ? inferPhase(questions) : Phase.valueOf(phase),
                savedAt.isEmpty()
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
                        : LocalDateTime.parse(savedAt),
                elapsed.isEmpty() ? 0 : Long.parseLong(elapsed),
                List.copyOf(questions));
    }

    /** Phase を持たない旧形式のファイル用。正解が全部埋まっていれば採点済みとみなす。 */
    private static Phase inferPhase(List<Question> questions) {
        long filled = questions.stream().filter(q -> !q.correct().isBlank()).count();
        if (filled > 0 && filled == questions.size()) return Phase.GRADED;
        return filled > 0 ? Phase.CHECKING : Phase.ANSWERING;
    }

    public void write(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = append(doc, doc, "ExamResults");

        Element exam = append(doc, root, "Exam");
        append(doc, exam, "Name").setTextContent(name);
        append(doc, exam, "Phase").setTextContent(phase.name());
        append(doc, exam, "SavedAt").setTextContent(savedAt.truncatedTo(ChronoUnit.SECONDS).toString());
        append(doc, exam, "ElapsedSeconds").setTextContent(String.valueOf(elapsedSeconds));

        Element results = append(doc, root, "Results");
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Element elem = append(doc, results, "Question");
            elem.setAttribute("id", String.valueOf(i + 1));
            append(doc, elem, "StudentAnswer").setTextContent(q.answer());
            append(doc, elem, "CorrectAnswer").setTextContent(q.correct());
            append(doc, elem, "IsCorrect").setTextContent(String.valueOf(q.isCorrect()));
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (FileOutputStream out = new FileOutputStream(file)) {
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        }
    }

    private static Element append(Document doc, Node parent, String tag) {
        Element elem = doc.createElement(tag);
        parent.appendChild(elem);
        return elem;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
}
