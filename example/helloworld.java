package hello;

/**
 * Class which says *hello* to the world.
 *
 * @since the beginning of time
 */
public class helloworld {

  public static String greeting_text = "Hello World";

  /**
   * Returns the text of a greeting.
   *
   * @returns greeting text
   */
  public static String greeting () {
    return greeting_text;
  }

  /**
   * main method which calls the greeting method
   */
  public static void main(String args[]) {
    System.out.println(greeting());
  }
}

