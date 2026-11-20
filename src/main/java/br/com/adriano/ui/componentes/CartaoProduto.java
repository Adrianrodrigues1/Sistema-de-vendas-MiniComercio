package br.com.adriano.ui.componentes;

import br.com.adriano.ui.pdv.*;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

public final class CartaoProduto extends PainelArredondado {
    private int quantidade = 1;
    private final JPanel seletor = Componentes.painel(new BorderLayout(), "painel");
    private final ProdutoPdv produto;
    public CartaoProduto(ProdutoPdv produto, IntConsumer adicionar) {
        super(new BorderLayout(0, 7), "painel"); this.produto = produto;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton imagem = Componentes.botao("Sem imagem", Icone.Tipo.IMAGEM, () -> adicionar.accept(quantidade));
        imagem.setIcon(new Icone(Icone.Tipo.IMAGEM, 38));
        imagem.setHorizontalTextPosition(SwingConstants.CENTER);
        imagem.setVerticalTextPosition(SwingConstants.BOTTOM);
        imagem.setPreferredSize(new Dimension(100, 116));
        imagem.setEnabled(produto.estoque() > 0);
        imagem.setToolTipText("Adicionar " + produto.nome() + " ao carrinho");
        imagem.getAccessibleContext().setAccessibleName("Adicionar " + produto.nome());
        GerenciadorTema.estilo(imagem, "suave", "muted");
        add(imagem, BorderLayout.NORTH);
        JPanel dados = Componentes.painel(new GridLayout(3, 1, 0, 1), "painel");
        JLabel nome = Componentes.texto(produto.nome(), 14, true); nome.setToolTipText(produto.nome());
        dados.add(nome); dados.add(Componentes.secundario("Cód: " + produto.codigo()));
        JLabel estoque = Componentes.secundario("Estoque: " + produto.estoque());
        estoque.setIcon(new Icone(Icone.Tipo.PRODUTO, 14)); dados.add(estoque);
        add(dados, BorderLayout.CENTER);
        JPanel rodape = Componentes.painel(new BorderLayout(2, 0), "painel");
        rodape.add(Componentes.texto(Dinheiro.formatar(produto.preco()), 13, true), BorderLayout.WEST);
        rodape.add(seletor, BorderLayout.EAST); atualizarQuantidade();
        add(rodape, BorderLayout.SOUTH);
    }
    private void atualizarQuantidade() {
        seletor.removeAll();
        seletor.add(new SeletorQuantidade(quantidade, 1, Math.max(1, produto.estoque()), valor -> {
            quantidade = valor; atualizarQuantidade();
        }));
        seletor.revalidate(); seletor.repaint();
    }
}
