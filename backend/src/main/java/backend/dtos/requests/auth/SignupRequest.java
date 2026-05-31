package backend.dtos.requests.auth;

import backend.annotations.strongPassword.StrongPassword;
import backend.models.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SignupRequest {
    @Email(message = "Invalid email format")
    @NotEmpty(message = "Email cannot be empty")
    private String email;

    @StrongPassword(minLength = 8)
    private String password;

    @NotEmpty(message = "Captcha cannot be empty")
    private String captcha;  

    @NotNull(message = "Role cannot be null")
    private UserRole role;
}