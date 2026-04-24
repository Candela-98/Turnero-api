package com.turnero.api.mapper;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.model.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(source = "customerId", target = "id")
    @Mapping(source = "nameCustomer", target = "name")
    @Mapping(source = "phoneCustomer", target = "phoneNumber")
    @Mapping(source = "creationDate", target = "createdIn")
    Customer toEntity(CustomerRequestDto dtoCustomer);
}
