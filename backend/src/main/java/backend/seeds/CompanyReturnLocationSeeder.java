package backend.seeds;

import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.repositories.CompanyReturnLocationRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class CompanyReturnLocationSeeder {

    private final CompanyReturnLocationRepository returnLocationRepository;

    public void seed(SeededCompanies companies) {
        returnLocation(companies.tech(),     "TechGadgets Returns Center", "500 Market St",      "San Francisco", "US", "94105");
        returnLocation(companies.style(),    "StyleHub Returns Hub",       "350 Fifth Ave",       "New York",      "US", "10118");
        returnLocation(companies.wellness(), "WellnessWorld Returns",      "220 Congress Ave",    "Austin",        "US", "78701");
        returnLocation(companies.home(),     "HomeNest Returns Facility",  "233 S Wacker Dr",     "Chicago",       "US", "60606");
        returnLocation(companies.sport(),    "SportZone Returns Depot",    "1700 Lincoln St",     "Denver",        "US", "80203");
    }

    private void returnLocation(Company company, String name, String address, String city, String country, String postalCode) {
        if (returnLocationRepository.countByCompanyId(company.getId()) > 0) return;
        CompanyReturnLocation loc = new CompanyReturnLocation();
        loc.setCompany(company);
        loc.setName(name);
        loc.setAddress(address);
        loc.setCity(city);
        loc.setCountry(country);
        loc.setPostalCode(postalCode);
        loc.setPrimary(true);
        returnLocationRepository.save(loc);
    }
}
