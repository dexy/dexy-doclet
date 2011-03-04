package it.dexy.jsondoclet;

import it.dexy.jsondoclet.Doclet;
import org.junit.Test;
import static org.junit.Assert.*;

public class DocletTest {

  @Test
  public void countLines() {
    String empty = "";
    String one_line = "abc";
    String two_lines = "abc\ndef";

    Doclet doclet = new Doclet();

    assertTrue(doclet.countLines(empty)==0);
    assertTrue(doclet.countLines(one_line)==1);
    assertTrue(doclet.countLines(two_lines)==2);
  }
}
