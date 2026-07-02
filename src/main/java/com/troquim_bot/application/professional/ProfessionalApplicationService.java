package com.troquim_bot.application.professional;

import org.springframework.stereotype.Service;

import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.ProfessionalRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application Service para gerenciar Professionals.
 * 
 * Responsabilidades:
 * - Criar novos professionals
 * - Buscar professionals por ID ou todos
 * - Atualizar dados de professionals
 * - Gerenciar ciclo de vida (ATIVO, INATIVO)
 */
@Service
public class ProfessionalApplicationService {

    private final ProfessionalRepository professionalRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public ProfessionalApplicationService() {
        this(new InMemoryProfessionalRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    public ProfessionalApplicationService(ProfessionalRepository professionalRepository) {
        this.professionalRepository = professionalRepository;
    }

    /**
     * Cria um novo Professional.
     * 
     * @param nome Nome do profissional
     * @param especialidades Especialidades do profissional
     * @param telefone Telefone de contato
     * @return Professional criado com status ATIVO
     */
    public Professional criarProfessional(String nome, Set<String> especialidades, String telefone) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (especialidades == null || especialidades.isEmpty()) {
            throw new IllegalArgumentException("Especialidades são obrigatórias");
        }
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        ProfessionalId id = ProfessionalId.generate();

        Professional professional = new Professional(
            id,
            nome.trim(),
            especialidades,
            telefone.trim()
        );

        return professionalRepository.save(professional);
    }

    /**
     * Busca um Professional por ID.
     * 
     * @param id ID do Professional
     * @return Optional com o Professional se encontrado
     */
    public Optional<Professional> buscarPorId(ProfessionalId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(professionalRepository.findById(id));
    }

    /**
     * Busca todos os Professionals.
     * 
     * @return Lista de todos os Professionals
     */
    public List<Professional> buscarTodos() {
        return professionalRepository.findAll();
    }

    /**
     * Atualiza um Professional.
     * Atualiza apenas os campos fornecidos (não nulos/vazios).
     * 
     * @param id ID do Professional
     * @param nome Novo nome (opcional)
     * @param especialidades Novas especialidades (opcional)
     * @param telefone Novo telefone (opcional)
     * @return Professional atualizado
     */
    public Professional atualizarProfessional(ProfessionalId id, String nome, Set<String> especialidades, String telefone) {
        Professional professional = getProfessionalOrThrow(id);

        // Atualiza apenas campos fornecidos
        if (nome != null && !nome.trim().isEmpty()) {
            professional.atualizarNome(nome);
        }

        if (especialidades != null && !especialidades.isEmpty()) {
            professional.atualizarEspecialidades(especialidades);
        }

        if (telefone != null && !telefone.trim().isEmpty()) {
            professional.atualizarTelefone(telefone);
        }

        return professionalRepository.save(professional);
    }

    /**
     * Inativa um Professional (transição para INATIVO).
     * 
     * @param id ID do Professional
     * @return Professional atualizado
     */
    public Professional inativarProfessional(ProfessionalId id) {
        Professional professional = getProfessionalOrThrow(id);
        professional.desativar();
        return professionalRepository.save(professional);
    }

    /**
     * Ativa um Professional (transição para ATIVO).
     * 
     * @param id ID do Professional
     * @return Professional atualizado
     */
    public Professional ativarProfessional(ProfessionalId id) {
        Professional professional = getProfessionalOrThrow(id);
        professional.ativar();
        return professionalRepository.save(professional);
    }

    /**
     * Verifica se existe um Professional com o ID informado.
     * 
     * @param id ID do Professional
     * @return true se existe
     */
    public boolean existeProfessional(ProfessionalId id) {
        if (id == null) {
            return false;
        }
        return professionalRepository.exists(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Professional getProfessionalOrThrow(ProfessionalId id) {
        return buscarPorId(id)
            .orElseThrow(() -> new IllegalStateException("Professional não encontrado com ID: " + id));
    }
}