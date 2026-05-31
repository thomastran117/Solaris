package backend.dtos.requests.auth;

import backend.annotations.strongPassword.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginRequest {
    @Email(message = "Invalid email format")
    @NotEmpty(message = "Email cannot be empty")
    private String email;

    @StrongPassword(minLength = 8)
    private String password;

    @NotEmpty(message = "Captcha cannot be empty")
    private String captcha;  
}