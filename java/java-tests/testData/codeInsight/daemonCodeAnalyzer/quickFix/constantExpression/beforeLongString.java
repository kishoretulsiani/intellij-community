// "Compute constant value of '"The quick brown fox jumps " + 100000 + " times" + " over the lazy dog"'" "true-preview"
class Test {
  void test() {
    // Do not display the result in action name
    String foo = "The quick brown fox jumps " + 10<caret>0000 + " times" + " over the lazy dog";
  }
}