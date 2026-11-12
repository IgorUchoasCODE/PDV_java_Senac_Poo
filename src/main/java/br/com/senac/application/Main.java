package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.service.EstoqueManage;
import br.com.senac.service.ManageNotas;
import br.com.senac.integracao.calendario.CalendarioService;
import br.com.senac.integracao.calendario.DiaNaoUtil;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.integracao.rios.NivelRioService;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.pagamento.FormaPagamentoDAO;
import br.com.senac.repository.dao.pessoa.PessoaDAO;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAVista;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {

    }
}
