package com.troquim_bot.controller.dto;

/**
 * Corpo do POST público de agendamento.
 *
 * Deliberadamente NÃO carrega: businessId, duração, horário de término, nome/preço do
 * serviço, customerId, appointmentId ou status. Qualquer campo extra que o cliente enviar
 * (ex.: um {@code businessId} forjado) é ignorado pelo Jackson por padrão — o tenant vem
 * SEMPRE do slug da URL, resolvido no servidor.
 */
public class PublicBookingRequest {

    private String serviceId;
    private String professionalId;
    private String date;
    private String time;
    private String customerName;
    private String customerPhone;

    public PublicBookingRequest() {
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }
}
