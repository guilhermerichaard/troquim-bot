package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de atualização de Business.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateBusinessRequest {

    private String name;
    private String phone;
    private String address;

    public UpdateBusinessRequest() {
    }

    public UpdateBusinessRequest(String name, String phone, String address) {
        this.name = name;
        this.phone = phone;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
