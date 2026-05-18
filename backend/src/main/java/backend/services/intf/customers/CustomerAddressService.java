package backend.services.intf.customers;

import java.util.UUID;
import backend.dtos.requests.address.CreateCustomerAddressRequest;
import backend.dtos.requests.address.UpdateCustomerAddressRequest;
import backend.dtos.responses.address.CustomerAddressResponse;

import java.util.List;

public interface CustomerAddressService {

    List<CustomerAddressResponse> listAddresses(UUID userId);

    CustomerAddressResponse getAddress(UUID userId, UUID addressId);

    CustomerAddressResponse createAddress(UUID userId, CreateCustomerAddressRequest request);

    CustomerAddressResponse updateAddress(UUID userId, UUID addressId, UpdateCustomerAddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    CustomerAddressResponse setDefault(UUID userId, UUID addressId);
}
