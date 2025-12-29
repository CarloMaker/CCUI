package com.cc.visualcc;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.psi.codeStyle.CodeStyleManager;

/**
 * Handles language-specific comment syntax for VisualCC styling.
 * Returns appropriate comment wrappers for different file types.
 */
public class VisualCCLanguageCommentor {

    public enum CommentStyle {
        SLASH("//"),           // Java, C, C++, Go, JS, TS, Kotlin, etc.
        HASH("#"),             // Python, Shell, Ruby, etc.
        HTML("<!--", "-->"),   // HTML, XML, Markdown
        STAR("/*", "*/"),      // CSS, Javadoc
        SEMICOLON(";");        // Lisp, Clojure, Assembly

        private final String[] delimiters;

        CommentStyle(String... delimiters) {
            this.delimiters = delimiters;
        }

        public String getPrefix() {
            return delimiters[0];
        }

        public String getSuffix() {
            return delimiters.length > 1 ? delimiters[1] : delimiters[0];
        }
    }

    /**
     * Determines the comment style for a given language.
     */
    public static CommentStyle getCommentStyle(Language language) {
        if (language == null) {
            return CommentStyle.SLASH;
        }

        String langID = language.getID().toLowerCase();

        // Hash-style comments
        if (langID.contains("python") || langID.equals("py") ||
            langID.contains("shell") || langID.equals("sh") || langID.equals("bash") ||
            langID.contains("ruby") || langID.equals("rb") ||
            langID.contains("perl") || langID.equals("pl") ||
            langID.contains("yaml") || langID.equals("yml") ||
            langID.contains("toml")) {
            return CommentStyle.HASH;
        }

        // HTML-style comments
        if (langID.contains("html") || langID.contains("xml") ||
            langID.contains("markdown") || langID.equals("md")) {
            return CommentStyle.HTML;
        }

        // Semicolon-style comments
        if (langID.contains("lisp") || langID.contains("clojure") ||
            langID.contains("assembly")) {
            return CommentStyle.SEMICOLON;
        }

        // Default to slash-style (Java, C, C++, Go, JS, TS, Kotlin, etc.)
        return CommentStyle.SLASH;
    }

    /**
     * Wraps text with the appropriate comment markers for the given language.
     */
    public static String wrapWithComments(Language language, String content) {
        CommentStyle style = getCommentStyle(language);
        return style.getPrefix() + content + (style == CommentStyle.HTML ? style.getSuffix() : "");
    }

    /**
     * Returns a multi-line comment block for the given language.
     */
    public static String getCommentBlock(Language language, String[] lines) {
        CommentStyle style = getCommentStyle(language);

        if (style == CommentStyle.HTML) {
            StringBuilder sb = new StringBuilder();
            sb.append(style.getPrefix()).append("\n");
            for (String line : lines) {
                sb.append("  ").append(line).append("\n");
            }
            sb.append(style.getSuffix());
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(style.getPrefix()).append(" ").append(line).append("\n");
        }
        // Remove trailing newline
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}
