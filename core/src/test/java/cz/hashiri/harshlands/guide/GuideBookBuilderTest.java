/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.guide;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuideBookBuilderTest {

    private YamlConfiguration minimalYaml() {
        String yaml = """
            guide:
              Title: "Test Manual"
              Author: "Tester"
              BackToContentsLabel: "« Contents"
              MissingContentFallback: "Missing."
              Cover:
                - "Welcome"
              Contents:
                Heading: "Contents"
                Entries:
                  - { key: "one", label: "1. First" }
                  - { key: "two", label: "2. Second" }
              Chapters:
                one:
                  - "First chapter, page 1"
                two:
                  - "Second chapter, page 1"
                  - "Second chapter, page 2"
            """;
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cfg;
    }

    @Test
    void builtBookHasCorrectPageCount() {
        // cover (1) + toc (1) + chapter "one" (1 page) + chapter "two" (2 pages) = 5 pages
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(minimalYaml());
        assertEquals(5, pages.size());
    }

    @Test
    void tocPageContainsChangePageClicksForEachChapter() {
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(minimalYaml());
        BaseComponent[] toc = pages.get(1); // ToC is page 2 (index 1)

        int changePageCount = 0;
        for (BaseComponent bc : flatten(toc)) {
            ClickEvent ce = bc.getClickEvent();
            if (ce != null && ce.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                changePageCount++;
            }
        }
        assertEquals(2, changePageCount, "expected 2 ToC chapter links");
    }

    @Test
    void tocPointsToCorrectChapterStartPages() {
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(minimalYaml());
        BaseComponent[] toc = pages.get(1);

        List<String> targets = new java.util.ArrayList<>();
        for (BaseComponent bc : flatten(toc)) {
            ClickEvent ce = bc.getClickEvent();
            if (ce != null && ce.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                targets.add(ce.getValue());
            }
        }
        // Chapter "one" starts on page 3 (cover=1, toc=2, one=3).
        // Chapter "two" starts on page 4.
        assertEquals("3", targets.get(0));
        assertEquals("4", targets.get(1));
    }

    @Test
    void itemTokensBecomeRunCommandClicks() {
        String yaml = """
            guide:
              Title: "T"
              Author: "A"
              BackToContentsLabel: ""
              MissingContentFallback: "Missing."
              Cover: [""]
              Contents:
                Heading: ""
                Entries:
                  - { key: "only", label: "1. Only" }
              Chapters:
                only:
                  - "Use %item_flint_hatchet% to chop"
            """;
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(cfg);
        BaseComponent[] chapterPage = pages.get(2); // cover=0, toc=1, only=2

        boolean found = false;
        for (BaseComponent bc : flatten(chapterPage)) {
            ClickEvent ce = bc.getClickEvent();
            if (ce != null && ce.getAction() == ClickEvent.Action.RUN_COMMAND
                && "/hl obtain flint_hatchet".equals(ce.getValue())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "expected a RUN_COMMAND click on /hl obtain flint_hatchet");
    }

    @Test
    void missingChapterKeyIsSkippedWithoutCrashing() {
        String yaml = """
            guide:
              Title: "T"
              Author: "A"
              BackToContentsLabel: ""
              MissingContentFallback: "Missing."
              Cover: [""]
              Contents:
                Heading: ""
                Entries:
                  - { key: "exists", label: "1. A" }
                  - { key: "missing", label: "2. B" }
              Chapters:
                exists:
                  - "Chapter A"
            """;
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(cfg);
        // cover + toc + exists = 3 pages (missing chapter dropped silently)
        assertEquals(3, pages.size());
    }

    @Test
    void fallbackPageWhenYamlHasNoChapters() {
        String yaml = "guide:\n  MissingContentFallback: \"Missing.\"\n";
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(cfg);
        assertEquals(1, pages.size());
        String text = new TextComponent(pages.get(0)).toPlainText();
        assertTrue(text.contains("Missing"), "fallback should include the missing text");
    }

    // Helper: walk a component tree flat.
    private static List<BaseComponent> flatten(BaseComponent[] components) {
        List<BaseComponent> out = new java.util.ArrayList<>();
        for (BaseComponent c : components) flattenInto(c, out);
        return out;
    }

    private static void flattenInto(BaseComponent c, List<BaseComponent> out) {
        out.add(c);
        if (c.getExtra() != null) {
            for (BaseComponent child : c.getExtra()) flattenInto(child, out);
        }
    }
}
