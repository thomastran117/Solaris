package backend.services.impl.customers;

import java.util.UUID;
import backend.dtos.requests.address.CreateCustomerAddressRequest;
import backend.dtos.requests.address.UpdateCustomerAddressRequest;
import backend.dtos.responses.address.CustomerAddressResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerAddress;
import backend.models.core.User;
import backend.repositories.CustomerAddressRepository;
import backend.repositories.UserRepository;
import backend.services.intf.customers.CustomerAddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerAddressServiceImpl implements CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;

    public CustomerAddressServiceImpl(CustomerAddressRepository addressRepository,
                                      UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listAddresses(UUID userId) {
        return addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAddressResponse getAddress(UUID userId, UUID addressId) {
        return toResponse(findOwned(userId, addressId));
    }

    @Override
    @Transactional
    public CustomerAddressResponse createAddress(UUID userId, CreateCustomerAddressRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (req.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }

        // If this is the user's first address, force it to be the default.
        boolean noExisting = addressRepository
                .findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).isEmpty();

        CustomerAddress address = new CustomerAddress();
        address.setUser(user);
        address.setLabel(req.getLabel());
        address.setRecipientName(req.getRecipientName());
        address.setStreet(req.getStreet());
        address.setStreet2(req.getStreet2());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setPostalCode(req.getPostalCode());
        address.setCountry(req.getCountry());
        address.setPhoneNumber(req.getPhoneNumber());
        address.setDefault(req.isDefault() || noExisting);

        return toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public CustomerAddressResponse updateAddress(UUID userId, UUID addressId,
                                                 UpdateCustomerAddressRequest req) {
        CustomerAddress address = findOwned(userId, addressId);

        if (req.getLabel() != null)         address.setLabel(req.getLabel());
        if (req.getRecipientName() != null)  address.setRecipientName(req.getRecipientName());
        if (req.getStreet() != null)         address.setStreet(req.getStreet());
        if (req.getStreet2() != null)        address.setStreet2(req.getStreet2());
        if (req.getCity() != null)           address.setCity(req.getCity());
        if (req.getState() != null)          address.setState(req.getState());
        if (req.getPostalCode() != null)     address.setPostalCode(req.getPostalCode());
        if (req.getCountry() != null)        address.setCountry(req.getCountry());
        if (req.getPhoneNumber() != null)    address.setPhoneNumber(req.getPhoneNumber());

        return toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        CustomerAddress address = findOwned(userId, addressId);
        addressRepository.delete(address);

        // Promote the oldest remaining address to default if the deleted one was default.
        if (address.isDefault()) {
            addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
                    .stream().findFirst().ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Override
    @Transactional
    public CustomerAddressResponse setDefault(UUID userId, UUID addressId) {
        findOwned(userId, addressId); // ownership check
        addressRepository.clearDefaultForUser(userId);
        CustomerAddress address = findOwned(userId, addressId);
        address.setDefault(true);
        return toResponse(addressRepository.save(address));
    }

    // -------------------------------------------------------------------------

    private CustomerAddress findOwned(UUID userId, UUID addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found with id: " + addressId));
    }

    private CustomerAddressResponse toResponse(CustomerAddress a) {
        return new CustomerAddressResponse(
                a.getId(),
                a.getUser().getId(),
                a.getLabel(),
                a.getRecipientName(),
                a.getStreet(),
                a.getStreet2(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getCountry(),
                a.getPhoneNumber(),
                a.isDefault(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
