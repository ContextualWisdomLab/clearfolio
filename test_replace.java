public class test_replace {
    public static void main(String[] args) {
        String a = "hello".replace('\u0000', ' ');
        System.out.println(a);
    }
}
