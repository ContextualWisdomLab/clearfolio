import java.io.File;
import java.util.Scanner;

public class CoverageCheck {
    public static void main(String[] args) throws Exception {
        System.out.println("Checking target/site/jacoco/jacoco.csv");
        File file = new File("target/site/jacoco/jacoco.csv");
        if (!file.exists()) {
            System.out.println("File not found");
            return;
        }
        Scanner scanner = new Scanner(file);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains("ViewerUiController")) {
                System.out.println(line);
            }
        }
        scanner.close();
    }
}
