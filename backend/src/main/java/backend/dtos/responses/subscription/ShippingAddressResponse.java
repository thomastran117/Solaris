package backend.dtos.responses.subscription;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressResponse {
    private String name;
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
