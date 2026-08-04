package com.troquim_bot.application.availability;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryBusinessHoursRepository;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O caso de uso canônico de disponibilidade, ponta a ponta na Application.
 *
 * TEMPO DETERMINÍSTICO: todo teste usa {@link RelogioDoNegocio#fixo}. Nenhuma asserção aqui
 * depende do relógio da máquina — sem isso, "horário passado não aparece" passaria de manhã
 * e falharia à noite.
 *
 * A semana de referência é fixada numa QUARTA-FEIRA (2026-09-02) para que "hoje", "amanhã"
 * e "sábado" sejam sempre os mesmos dias da semana, em qualquer execução.
 */
@DisplayName("ConsultarDisponibilidade - expediente ∩ profissional − agendamentos")
class ConsultarDisponibilidadeTest {

    private static final BusinessId SALAO = TestTenants.PILOT;
    private static final BusinessId OUTRO_SALAO = TestTenants.OUTRO;

    /** Quarta-feira. Toda a suíte é ancorada aqui. */
    private static final LocalDate QUARTA = LocalDate.of(2026, 9, 2);
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 5);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 9, 6);
    private static final LocalDateTime MADRUGADA_DA_QUARTA = QUARTA.atTime(1, 0);

    private InMemoryServiceRepository servicos;
    private InMemoryProfessionalRepository profissionais;
    private InMemoryBusinessHoursRepository expedientes;
    private InMemoryAvailabilityRepository disponibilidades;
    private InMemoryAppointmentRepository appointments;

    private ConsultarDisponibilidade consultar;

    private Service servicoDe1h;
    private Professional malu;

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    /** Segunda a sexta 09:00-12:00 e 13:00-18:00; sábado 09:00-14:00; domingo FECHADO. */
    private static BusinessHours expedientePadrao() {
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                DiaSemana.QUINTA, DiaSemana.SEXTA)) {
            semana.put(dia, List.of(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0)));
        }
        semana.put(DiaSemana.SABADO, List.of(periodo(9, 0, 14, 0)));
        return BusinessHours.deSemana(semana);
    }

    private ConsultarDisponibilidade comRelogioEm(LocalDateTime instante) {
        return new ConsultarDisponibilidade(
                new ConsultarCatalogo(servicos, profissionais),
                expedientes, disponibilidades, appointments,
                RelogioDoNegocio.fixo(instante));
    }

    private Service servico(BusinessId negocio, String nome, int minutos) {
        return servicos.salvar(Service.novoSemPreco(ServiceId.generate(), negocio, nome, null,
                ServiceDuration.ofMinutes(minutos)));
    }

    private Professional profissional(BusinessId negocio, String nome, Set<ServiceId> habilitados) {
        return profissionais.salvar(new Professional(ProfessionalId.generate(), negocio, nome,
                habilitados, Set.of(), "+5511999990000"));
    }

    /** Disponibilidade do profissional idêntica ao expediente do negócio. */
    private void disponibilizarSemanaInteira(BusinessId negocio, Professional profissional,
                                             BusinessHours expediente) {
        expediente.porDiaDaSemana().forEach((dia, periodos) -> periodos.forEach(p ->
                disponibilidades.salvar(new Availability(AvailabilityId.generate(), negocio,
                        profissional.getId(), dia, p))));
    }

    private Appointment agendar(BusinessId negocio, ProfessionalId profissional, LocalDate data,
                                LocalTime inicio, LocalTime fim) {
        Appointment appointment = new Appointment(AppointmentId.generate(), negocio,
                CustomerId.from(UUID.randomUUID()), profissional, servicoDe1h.getId(),
                AvailabilityId.generate(), data, inicio, fim);
        return appointments.save(appointment);
    }

    @BeforeEach
    void montarSalao() {
        servicos = new InMemoryServiceRepository();
        profissionais = new InMemoryProfessionalRepository();
        expedientes = new InMemoryBusinessHoursRepository();
        disponibilidades = new InMemoryAvailabilityRepository();
        appointments = new InMemoryAppointmentRepository();

        servicoDe1h = servico(SALAO, "Cabelo", 60);
        malu = profissional(SALAO, "Malu", Set.of(servicoDe1h.getId()));

        expedientes.salvar(SALAO, expedientePadrao());
        disponibilizarSemanaInteira(SALAO, malu, expedientePadrao());

        consultar = comRelogioEm(MADRUGADA_DA_QUARTA);
    }

    // ==================== TENANT ====================

    @Nested
    @DisplayName("Isolamento entre negócios")
    class Tenant {

        @Test
        @DisplayName("dois negócios podem ter expedientes DIFERENTES")
        void expedientesDiferentesPorNegocio() {
            // O outro salão abre só à tarde, e num único período.
            Service servicoB = servico(OUTRO_SALAO, "Barba", 60);
            Professional rui = profissional(OUTRO_SALAO, "Rui", Set.of(servicoB.getId()));
            BusinessHours soATarde = BusinessHours.deSemana(
                    Map.of(DiaSemana.QUARTA, List.of(periodo(14, 0, 17, 0))));
            expedientes.salvar(OUTRO_SALAO, soATarde);
            disponibilizarSemanaInteira(OUTRO_SALAO, rui, soATarde);

            var doSalao = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);
            var doOutro = consultar.doDia(OUTRO_SALAO, servicoB.getId(), rui.getId(), QUARTA);

            assertThat(doSalao.horarios()).startsWith(LocalTime.of(9, 0));
            assertThat(doOutro.horarios()).startsWith(LocalTime.of(14, 0));
            assertThat(doOutro.horarios()).doesNotContain(LocalTime.of(9, 0));
            assertThat(doSalao.horarios()).isNotEqualTo(doOutro.horarios());
        }

        @Test
        @DisplayName("o MESMO UUID de profissional em dois negócios não mistura dados")
        void mesmoProfessionalIdNaoMistura() {
            // Cenário adversário: mesmo UUID cadastrado nos dois salões.
            ProfessionalId compartilhado = malu.getId();
            Service servicoB = servico(OUTRO_SALAO, "Barba", 60);
            profissionais.salvar(new Professional(compartilhado, OUTRO_SALAO, "Homônimo",
                    Set.of(servicoB.getId()), Set.of(), "+5511911111111"));
            BusinessHours outroExpediente = BusinessHours.deSemana(
                    Map.of(DiaSemana.QUARTA, List.of(periodo(15, 0, 16, 0))));
            expedientes.salvar(OUTRO_SALAO, outroExpediente);
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), OUTRO_SALAO,
                    compartilhado, DiaSemana.QUARTA, periodo(15, 0, 16, 0)));

            var doOutro = consultar.doDia(OUTRO_SALAO, servicoB.getId(), compartilhado, QUARTA);

            // O salão B enxerga só a sua janela, mesmo com o profissional de id idêntico.
            assertThat(doOutro.horarios()).containsExactly(LocalTime.of(15, 0));
        }

        @Test
        @DisplayName("uma empresa não lê a disponibilidade da outra")
        void naoLeDisponibilidadeAlheia() {
            // O salão B tem catálogo e expediente, mas NENHUMA disponibilidade cadastrada.
            Service servicoB = servico(OUTRO_SALAO, "Barba", 60);
            Professional rui = profissional(OUTRO_SALAO, "Rui", Set.of(servicoB.getId()));
            expedientes.salvar(OUTRO_SALAO, expedientePadrao());

            var doOutro = consultar.doDia(OUTRO_SALAO, servicoB.getId(), rui.getId(), QUARTA);

            // A disponibilidade da Malu (salão A) não vaza para cá.
            assertThat(doOutro.horarios()).isEmpty();
            assertThat(doOutro.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.PROFISSIONAL_SEM_DISPONIBILIDADE);
        }

        @Test
        @DisplayName("BusinessId é obrigatório — não há consulta sem tenant")
        void tenantObrigatorio() {
            assertThatThrownBy(() -> consultar.doDia(null, servicoDe1h.getId(), malu.getId(), QUARTA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BusinessId");
        }
    }

    // ==================== EXPEDIENTE ====================

    @Nested
    @DisplayName("Expediente")
    class Expediente {

        @Test
        @DisplayName("o INTERVALO DE ALMOÇO some da lista: nada entre 12:00 e 13:00")
        void almocoNaoGeraHorario() {
            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).contains(LocalTime.of(11, 0));
            assertThat(agenda.horarios()).doesNotContain(
                    LocalTime.of(11, 15), LocalTime.of(12, 0), LocalTime.of(12, 15),
                    LocalTime.of(12, 30), LocalTime.of(12, 45));
            assertThat(agenda.horarios()).contains(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("SÁBADO fecha às 14:00 e a lista respeita isso")
        void sabadoTemHorarioProprio() {
            var sabado = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), SABADO);

            assertThat(sabado.horarios()).startsWith(LocalTime.of(9, 0));
            assertThat(sabado.horarios()).endsWith(LocalTime.of(13, 0));
            // Na quarta ainda há vaga às 17:00; no sábado, não.
            assertThat(sabado.horarios()).doesNotContain(LocalTime.of(17, 0));
        }

        @Test
        @DisplayName("DOMINGO é fechado — condição explícita, não lista vazia sem motivo")
        void domingoFechado() {
            var domingo = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), DOMINGO);

            assertThat(domingo.horarios()).isEmpty();
            assertThat(domingo.condicao()).isEqualTo(ConsultarDisponibilidade.Condicao.DIA_FECHADO);
        }

        @Test
        @DisplayName("negócio sem expediente recebe EXPEDIENTE_NAO_CONFIGURADO, não 'cheio'")
        void expedienteNaoConfigurado() {
            Service servicoB = servico(OUTRO_SALAO, "Barba", 60);
            Professional rui = profissional(OUTRO_SALAO, "Rui", Set.of(servicoB.getId()));
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), OUTRO_SALAO,
                    rui.getId(), DiaSemana.QUARTA, periodo(9, 0, 18, 0)));

            var agenda = consultar.doDia(OUTRO_SALAO, servicoB.getId(), rui.getId(), QUARTA);

            assertThat(agenda.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.EXPEDIENTE_NAO_CONFIGURADO);
        }

        @Test
        @DisplayName("a disponibilidade do profissional NÃO amplia o expediente do negócio")
        void disponibilidadeNaoAmpliaExpediente() {
            // A profissional se declara disponível das 07:00 às 22:00 na quarta.
            disponibilidades.listarPorNegocio(SALAO).forEach(a ->
                    disponibilidades.remover(SALAO, a.getId()));
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), SALAO,
                    malu.getId(), DiaSemana.QUARTA, periodo(7, 0, 22, 0)));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            // Ainda assim, vale a interseção com o expediente: 09-12 e 13-18.
            assertThat(agenda.horarios()).startsWith(LocalTime.of(9, 0));
            assertThat(agenda.horarios()).endsWith(LocalTime.of(17, 0));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(7, 0), LocalTime.of(21, 0));
        }

        @Test
        @DisplayName("profissional disponível só de manhã não recebe horários da tarde")
        void intersecaoRestringe() {
            disponibilidades.listarPorNegocio(SALAO).forEach(a ->
                    disponibilidades.remover(SALAO, a.getId()));
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), SALAO,
                    malu.getId(), DiaSemana.QUARTA, periodo(9, 0, 12, 0)));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).endsWith(LocalTime.of(11, 0));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("disponibilidade INATIVA não conta")
        void disponibilidadeInativaNaoConta() {
            disponibilidades.listarPorNegocio(SALAO).forEach(a -> {
                a.inativar();
                disponibilidades.salvar(a);
            });

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.PROFISSIONAL_SEM_DISPONIBILIDADE);
        }
    }

    // ==================== SERVIÇO E DURAÇÃO ====================

    @Nested
    @DisplayName("Duração real do serviço")
    class Duracao {

        private ConsultarDisponibilidade.AgendaDoDia paraServicoDe(int minutos) {
            Service outro = servico(SALAO, "Serviço de " + minutos + "min", minutos);
            malu.habilitarPara(outro.getId());
            profissionais.salvar(malu);
            return consultar.doDia(SALAO, outro.getId(), malu.getId(), QUARTA);
        }

        @Test
        @DisplayName("serviço de 30 minutos gera apenas slots em que cabe INTEGRALMENTE")
        void servicoDe30Minutos() {
            var agenda = paraServicoDe(30);

            // Último da manhã: 11:30 (termina 12:00). 11:45 terminaria 12:15, no almoço.
            assertThat(agenda.horarios()).contains(LocalTime.of(11, 30));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(11, 45));
            // Último da tarde: 17:30 (termina 18:00, o fechamento).
            assertThat(agenda.horarios()).endsWith(LocalTime.of(17, 30));
        }

        @Test
        @DisplayName("serviço de 45 minutos usa 45 minutos, não uma hora arredondada")
        void servicoDe45Minutos() {
            var agenda = paraServicoDe(45);

            assertThat(agenda.horarios()).contains(LocalTime.of(11, 15));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(11, 30));
            assertThat(agenda.horarios()).endsWith(LocalTime.of(17, 15));
        }

        @Test
        @DisplayName("serviço de 1h30 não aparece perto demais do fechamento")
        void servicoDe1h30() {
            var agenda = paraServicoDe(90);

            // Manhã 09:00-12:00: último início às 10:30, exatamente como no enunciado.
            assertThat(agenda.horarios()).contains(LocalTime.of(10, 30));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(10, 45), LocalTime.of(11, 0));
            // Tarde 13:00-18:00: último início às 16:30.
            assertThat(agenda.horarios()).endsWith(LocalTime.of(16, 30));
        }

        @Test
        @DisplayName("serviço de 1h40 usa sua duração REAL")
        void servicoDe1h40() {
            var agenda = paraServicoDe(100);

            // Manhã 09:00-12:00: 10:15 termina 11:55 (último que cabe); 10:30 iria a 12:10.
            assertThat(agenda.horarios()).contains(LocalTime.of(10, 15));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(10, 30));
            // Tarde 13:00-18:00: último início às 16:15 (termina 17:55).
            assertThat(agenda.horarios()).endsWith(LocalTime.of(16, 15));
        }

        @Test
        @DisplayName("serviço mais longo que qualquer período não gera slot nenhum")
        void servicoLongoDemais() {
            var agenda = paraServicoDe(360); // 6h; o maior período tem 5h

            assertThat(agenda.horarios()).isEmpty();
            assertThat(agenda.condicao()).isEqualTo(ConsultarDisponibilidade.Condicao.AGENDA_CHEIA);
        }

        @Test
        @DisplayName("a duração vem do CATÁLOGO: mudá-la muda a lista, sem nenhum parâmetro externo")
        void duracaoVemDoCatalogo() {
            var comUmaHora = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);
            assertThat(comUmaHora.horarios()).endsWith(LocalTime.of(17, 0));

            servicoDe1h.atualizarDuracao(ServiceDuration.ofMinutes(120));
            servicos.salvar(servicoDe1h);

            var comDuasHoras = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);
            assertThat(comDuasHoras.horarios()).endsWith(LocalTime.of(16, 0));
        }
    }

    // ==================== CATÁLOGO ====================

    @Nested
    @DisplayName("Validação de catálogo")
    class Catalogo {

        @Test
        @DisplayName("serviço INATIVO é rejeitado com condição explícita")
        void servicoInativo() {
            servicoDe1h.desativar();
            servicos.salvar(servicoDe1h);

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.SERVICO_INDISPONIVEL);
        }

        @Test
        @DisplayName("serviço de OUTRO tenant é rejeitado")
        void servicoDeOutroTenant() {
            var agenda = consultar.doDia(OUTRO_SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.SERVICO_INDISPONIVEL);
        }

        @Test
        @DisplayName("profissional NÃO HABILITADO para o serviço é rejeitado")
        void profissionalNaoHabilitado() {
            Professional rita = profissional(SALAO, "Rita", Set.of());
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), SALAO,
                    rita.getId(), DiaSemana.QUARTA, periodo(9, 0, 18, 0)));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), rita.getId(), QUARTA);

            assertThat(agenda.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.PROFISSIONAL_INDISPONIVEL);
        }

        @Test
        @DisplayName("profissional INATIVO é rejeitado, mesmo habilitado e com disponibilidade")
        void profissionalInativo() {
            malu.desativar();
            profissionais.salvar(malu);

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).isEmpty();
            assertThat(agenda.condicao()).isIn(
                    ConsultarDisponibilidade.Condicao.SERVICO_INDISPONIVEL,
                    ConsultarDisponibilidade.Condicao.PROFISSIONAL_INDISPONIVEL);
        }
    }

    // ==================== CONFLITOS ====================

    @Nested
    @DisplayName("Conflito com agendamentos")
    class Conflitos {

        @Test
        @DisplayName("appointment com o MESMO início remove o slot")
        void mesmoInicio() {
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(10, 0), LocalTime.of(11, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("appointment que começa no MEIO do serviço candidato remove o slot")
        void conflitoParcial() {
            // Ocupado 10:30-11:30. O candidato das 10:00 dura até 11:00 e SE SOBREPÕE.
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(10, 30), LocalTime.of(11, 30));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            // Se a regra comparasse apenas horários INICIAIS, 10:00 sobreviveria — e a
            // cliente seria marcada em cima de um atendimento em andamento.
            assertThat(agenda.horarios()).doesNotContain(
                    LocalTime.of(10, 0), LocalTime.of(10, 15), LocalTime.of(10, 30),
                    LocalTime.of(10, 45), LocalTime.of(11, 0), LocalTime.of(11, 15));
            assertThat(agenda.horarios()).contains(LocalTime.of(9, 30));
        }

        @Test
        @DisplayName("appointment que TERMINA exatamente no início do candidato NÃO conflita")
        void bordaFimIgualInicio() {
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(9, 0), LocalTime.of(10, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).contains(LocalTime.of(10, 0));
            assertThat(agenda.horarios()).doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 45));
        }

        @Test
        @DisplayName("appointment que COMEÇA exatamente no fim do candidato NÃO conflita")
        void bordaInicioIgualFim() {
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(10, 0), LocalTime.of(11, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            // Candidato 09:00-10:00 encosta no ocupado e continua válido.
            assertThat(agenda.horarios()).contains(LocalTime.of(9, 0));
        }

        @Test
        @DisplayName("appointment CANCELADO não bloqueia")
        void canceladoNaoBloqueia() {
            Appointment cancelado = agendar(SALAO, malu.getId(), QUARTA,
                    LocalTime.of(10, 0), LocalTime.of(11, 0));
            cancelado.cancelar();
            appointments.save(cancelado);

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).contains(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("agendamento de OUTRA empresa não bloqueia")
        void deOutraEmpresaNaoBloqueia() {
            agendar(OUTRO_SALAO, malu.getId(), QUARTA, LocalTime.of(10, 0), LocalTime.of(11, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).contains(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("agendamento de OUTRO profissional não bloqueia")
        void deOutroProfissionalNaoBloqueia() {
            Professional rita = profissional(SALAO, "Rita", Set.of(servicoDe1h.getId()));
            agendar(SALAO, rita.getId(), QUARTA, LocalTime.of(10, 0), LocalTime.of(11, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).contains(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("agenda tomada por inteiro devolve AGENDA_CHEIA, não 'dia fechado'")
        void agendaCheia() {
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(9, 0), LocalTime.of(12, 0));
            agendar(SALAO, malu.getId(), QUARTA, LocalTime.of(13, 0), LocalTime.of(18, 0));

            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).isEmpty();
            assertThat(agenda.condicao()).isEqualTo(ConsultarDisponibilidade.Condicao.AGENDA_CHEIA);
        }
    }

    // ==================== TEMPO ====================

    @Nested
    @DisplayName("Tempo, com relógio fixo")
    class Tempo {

        @Test
        @DisplayName("horário que JÁ PASSOU hoje não aparece")
        void horarioPassadoNoDiaAtual() {
            // Relógio fixado às 10:20 da própria quarta.
            var aoMeioDaManha = comRelogioEm(QUARTA.atTime(10, 20));

            var agenda = aoMeioDaManha.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).doesNotContain(
                    LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(10, 15));
            assertThat(agenda.horarios()).startsWith(LocalTime.of(10, 30));
        }

        @Test
        @DisplayName("data PASSADA devolve DATA_PASSADA, e nunca horários")
        void dataPassada() {
            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(),
                    QUARTA.minusDays(1));

            assertThat(agenda.horarios()).isEmpty();
            assertThat(agenda.condicao()).isEqualTo(ConsultarDisponibilidade.Condicao.DATA_PASSADA);
        }

        @Test
        @DisplayName("o mesmo dia consultado de madrugada oferece a manhã inteira")
        void deMadrugadaTudoDisponivel() {
            var agenda = consultar.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).startsWith(LocalTime.of(9, 0));
        }

        @Test
        @DisplayName("depois do fechamento, o dia de hoje fica sem horário — e diz por quê")
        void depoisDoFechamento() {
            var aNoite = comRelogioEm(QUARTA.atTime(23, 0));

            var agenda = aNoite.doDia(SALAO, servicoDe1h.getId(), malu.getId(), QUARTA);

            assertThat(agenda.horarios()).isEmpty();
            assertThat(agenda.condicao()).isEqualTo(ConsultarDisponibilidade.Condicao.AGENDA_CHEIA);
        }
    }

    // ==================== JANELA ====================

    @Nested
    @DisplayName("Janela de datas")
    class Janela {

        @Test
        @DisplayName("dias com vaga excluem o domingo fechado")
        void domingoNaoEntra() {
            var janela = consultar.naJanela(SALAO, servicoDe1h.getId(), malu.getId(),
                    QUARTA, QUARTA.plusDays(7));

            assertThat(janela.datas()).contains(QUARTA, SABADO);
            assertThat(janela.datas()).doesNotContain(DOMINGO);
        }

        @Test
        @DisplayName("janela inteira sem expediente devolve o motivo ESTRUTURAL, não 'cheia'")
        void motivoEstruturalNaJanela() {
            Service servicoB = servico(OUTRO_SALAO, "Barba", 60);
            Professional rui = profissional(OUTRO_SALAO, "Rui", Set.of(servicoB.getId()));
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), OUTRO_SALAO,
                    rui.getId(), DiaSemana.QUARTA, periodo(9, 0, 18, 0)));

            var janela = consultar.naJanela(OUTRO_SALAO, servicoB.getId(), rui.getId(),
                    QUARTA, QUARTA.plusDays(7));

            assertThat(janela.datas()).isEmpty();
            assertThat(janela.condicao())
                    .isEqualTo(ConsultarDisponibilidade.Condicao.EXPEDIENTE_NAO_CONFIGURADO);
        }
    }

    @Test
    @DisplayName("estaLivre concorda com a lista de horários")
    void estaLivreConcordaComALista() {
        assertThat(consultar.estaLivre(SALAO, servicoDe1h.getId(), malu.getId(),
                QUARTA, LocalTime.of(10, 0))).isTrue();
        assertThat(consultar.estaLivre(SALAO, servicoDe1h.getId(), malu.getId(),
                QUARTA, LocalTime.of(12, 30))).isFalse();
    }
}
