import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest(classes =  PasswordServiceTest.class)
public class PasswordServiceTest {

    @Test
    void testBCryptPasswordEncoder() {
        String password = "Admin123";

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String encode = encoder.encode(password);
        System.out.println(encode);
    }
}
