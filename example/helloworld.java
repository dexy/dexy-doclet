package helloworld;

/**
 * Class which says hello, world!
 */
public class helloworld {

  public static String greeting_text = "Hello World";

  /**
   * Returns the text of a greeting.
   */
  public static String greeting () {
    return greeting_text;
  }
  public static void main(String args[]) {
    System.out.println(greeting());
  }
}

