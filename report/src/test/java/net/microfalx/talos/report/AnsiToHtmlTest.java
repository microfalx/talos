package net.microfalx.talos.report;

import net.microfalx.resource.ClassPathResource;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnsiToHtmlTest {

    @Test
    void formatColors() {
        // assertEquals("", buffer().success("Test").toString());
    }

    @Test
    void parseColors() throws IOException {
        AnsiToHtml ansiToHtml = new AnsiToHtml();
        Resource resource = ansiToHtml.transform(ClassPathResource.file("ansi/basic_colors.txt"));
        assertEquals("[<span style='font-weight: bold;color: 0000ff'>INFO</span>] Scanning for projects...\n" +
                     "[<span style='font-weight: bold;color: ff0000'>ERROR</span>] Internal error: java.lang.IllegalArgumentException: version can neither be null, empty nor blank -> <span style='font-weight: bold'>[Help 1]</span>\n", resource.loadAsString());
    }

}