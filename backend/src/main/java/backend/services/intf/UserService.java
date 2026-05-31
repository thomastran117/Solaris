package backend.services.intf;

import backend.models.core.User;

public interface UserService {
    User login(String email, String password);

    User signup(String email, String password, String usertype);

    void activateUser(long userId);

    boolean changePassword(long id, String password);

    boolean delete(long id);

    User getUserByID(long id);

    long getID(String email);

    User loginOrSignupGoogle(String email);

    User loginOrSignupMicrosoft(String email);

    User loginOrSignupApple(String email);
}
