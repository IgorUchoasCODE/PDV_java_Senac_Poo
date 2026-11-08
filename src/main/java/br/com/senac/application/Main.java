package br.com.senac.application;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;

public class Main {
    public static void main(String[] args) {
        try {
            ProdutoDAO pDao = new ProdutoDAO();
            UmDAO uDao = new UmDAO();

            // Se o banco (embarcado H2 ou MySQL) estiver vazio, cria os registros iniciais de teste
            if (pDao.listarTodos().isEmpty()) {
                Um unidade = uDao.buscarPorSigla("UN");
                if (unidade == null) {
                    unidade = new Um("UN", "Unidade", "Unidade padrão inteira", BigDecimal.ONE);
                    uDao.salvar(unidade);
                }

                ProdutoFisico mouse = new ProdutoFisico("Mouse Gamer Sem Fio", unidade);
                mouse.setDescricao("Mouse óptico 16000 DPI com receptor USB");
                mouse.setPrecoVenda(new BigDecimal("199.90"));
                pDao.salvar(mouse);

                ProdutoDigital curso = new ProdutoDigital("Curso Completo de Java e JPA", unidade);
                curso.setDescricao("Acesso vitalício às aulas e materiais em PDF");
                curso.setPrecoVenda(new BigDecimal("297.00"));
                curso.adicionarLink(new LinkProdutoDigital("Plataforma de Aulas", "https://senac.com.br/aulas/java-jpa"));
                curso.adicionarLink(new LinkProdutoDigital("Apostila em PDF", "https://senac.com.br/materiais/apostila-jpa.pdf"));
                pDao.salvar(curso);

                ProdutoFisico teclado = new ProdutoFisico("Teclado Mecânico RGB", unidade);
                teclado.setDescricao("Teclado mecânico switch blue ABNT2");
                teclado.setPrecoVenda(new BigDecimal("289.90"));
                pDao.salvar(teclado);

                ProdutoFisico kitGamer = new ProdutoFisico("Kit Gamer Completo (Teclado + 2 Mouses)", unidade);
                kitGamer.setDescricao("Combo promocional contendo teclado mecânico e mouses");
                kitGamer.setPrecoVenda(new BigDecimal("599.90"));
                kitGamer.adicionarItemConjunto(teclado, new BigDecimal("1.000000"));
                kitGamer.adicionarItemConjunto(mouse, new BigDecimal("2.000000"));
                pDao.salvar(kitGamer);
            }

            System.out.println(pDao.buscarPorId(2L));
            System.out.println(pDao.buscarPorId(4L));
        } finally {
            JPAUtil.close();
        }
    }
}
