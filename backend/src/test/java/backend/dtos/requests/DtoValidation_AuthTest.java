package backend.dtos.requests;

import backend.dtos.requests.auth.ChangePasswordRequest;
import backend.dtos.requests.auth.LoginRequest;
import backend.dtos.requests.auth.SignupRequest;
import org.junit.jupiter.api.Test;

class DtoValidation_AuthTest extends AbstractDtoValidationTest {

    // ─── LoginRequest ─────────────────────────────────────────────────────────

    @Test
    void loginRequest_valid_noViolations() {
        assertValid(new LoginRequest("user@example.com", "Password1!", "captcha-token"));
    }

    @Test
    void loginRequest_blankEmail_violation() {
        assertViolation(new LoginRequest("", "Password1!", "cap"), "email");
    }

    @Test
    void loginRequest_invalidEmailFormat_violation() {
        assertViolation(new LoginRequest("not-an-email", "Password1!", "cap"), "email");
    }

    @Test
    void loginRequest_blankPassword_violation() {
        assertViolation(new LoginRequest("user@example.com", "", "cap"), "password");
    }

    @Test
    void loginRequest_blankCaptcha_violation() {
        assertViolation(new LoginRequest("user@example.com", "Password1!", ""), "captcha");
    }

    // ─── SignupRequest ────────────────────────────────────────────────────────

    @Test
    void signupRequest_valid_noViolations() {
        assertValid(new SignupRequest("user@example.com", "Password1!", "cap123"));
    }

    @Test
    void signupRequest_blankEmail_violation() {
        assertViolation(new SignupRequest("", "Password1!", "cap"), "email");
    }

    @Test
    void signupRequest_invalidEmailFormat_violation() {
        assertViolation(new SignupRequest("not-email", "Password1!", "cap"), "email");
    }

    @Test
    void signupRequest_passwordTooShort_violation() {
        assertViolation(new SignupRequest("u@e.com", "Ab1!", "cap"), "password");
    }

    @Test
    void signupRequest_passwordNoUppercase_violation() {
        assertViolation(new SignupRequest("u@e.com", "password1!", "cap"), "password");
    }

    @Test
    void signupRequest_passwordNoDigit_violation() {
        assertViolation(new SignupRequest("u@e.com", "Password!", "cap"), "password");
    }

    @Test
    void signupRequest_passwordNoSpecialChar_violation() {
        assertViolation(new SignupRequest("u@e.com", "Password1", "cap"), "password");
    }

    @Test
    void signupRequest_blankCaptcha_violation() {
        assertViolation(new SignupRequest("u@e.com", "Password1!", ""), "captcha");
    }

    // ─── ChangePasswordRequest ────────────────────────────────────────────────

    @Test
    void changePasswordRequest_valid_noViolations() {
        assertValid(new ChangePasswordRequest("reset-token-123", "NewPass1!"));
    }

    @Test
    void changePasswordRequest_blankToken_violation() {
        assertViolation(new ChangePasswordRequest("", "NewPass1!"), "token");
    }

    @Test
    void changePasswordRequest_weakPassword_tooShort_violation() {
        assertViolation(new ChangePasswordRequest("token", "Ab1!"), "password");
    }

    @Test
    void changePasswordRequest_weakPassword_noUppercase_violation() {
        assertViolation(new ChangePasswordRequest("token", "newpass1!"), "password");
    }

    @Test
    void changePasswordRequest_weakPassword_noSpecialChar_violation() {
        assertViolation(new ChangePasswordRequest("token", "NewPass12"), "password");
    }
}
