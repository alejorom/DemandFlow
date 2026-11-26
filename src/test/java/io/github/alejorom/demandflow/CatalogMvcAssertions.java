package io.github.alejorom.demandflow;

import java.io.StringReader;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural checks on rendered HTML; no browser execution is claimed. */
final class CatalogMvcAssertions {
    static String csrfToken(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("server-generated CSRF field").isTrue();
        return matcher.group(1);
    }

    static void safeHtml(String html) throws Exception {
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private void check(HTML.Tag tag, MutableAttributeSet attributes) {
                assertThat(tag).isNotIn(HTML.Tag.STYLE, HTML.Tag.SCRIPT);
                var names = attributes.getAttributeNames();
                while (names.hasMoreElements()) {
                    Object key = names.nextElement();
                    String name = key.toString().toLowerCase(Locale.ROOT);
                    assertThat(name.equals("style") || name.startsWith("on")).isFalse();
                    if (name.equals("href") || name.equals("src") || name.equals("action")) {
                        for (String prefix : new String[] {"http:", "https:", "//", "data:", "javascript:"}) {
                            assertThat(attributes.getAttribute(key).toString().toLowerCase(Locale.ROOT)).doesNotStartWith(prefix);
                        }
                    }
                }
            }
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                check(tag, attributes);
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                check(tag, attributes);
            }
        }, true);
    }
    private CatalogMvcAssertions() { }
}
