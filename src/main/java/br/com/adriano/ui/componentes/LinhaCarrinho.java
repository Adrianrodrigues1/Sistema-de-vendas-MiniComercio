package br.com.adriano.ui.componentes;

import br.com.adriano.ui.pdv.*;
import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

public final class LinhaCarrinho extends PainelArredondado {
    public LinhaCarrinho(Carrinho.Item item, IntConsumer quantidade, Runnable remover) {
        super(new BorderLayout(7, 0), "painel");
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(360, 90)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        JComponent imagem = Componentes.placeholder(58); imagem.setPreferredSize(new Dimension(56, 58));
        add(imagem, BorderLayout.WEST);
        JPanel dados = Componentes.painel(new BorderLayout(0, 4), "painel");
        JPanel titulo = Componentes.painel(new BorderLayout(), "painel");
        JLabel nome = Componentes.texto(item.produto().nome(), 14, true); nome.setToolTipText(item.produto().nome());
        titulo.add(nome, BorderLayout.CENTER);
        JButton excluir = Componentes.botao("", Icone.Tipo.X, remover);
        excluir.setPreferredSize(new Dimension(24, 24)); excluir.setMargin(new Insets(0, 0, 0, 0));
        excluir.getAccessibleContext().setAccessibleName("Remover " + item.produto().nome());
        titulo.add(excluir, BorderLayout.EAST); dados.add(titulo, BorderLayout.NORTH);
        dados.add(Componentes.secundario("Cód: " + item.produto().codigo()), BorderLayout.CENTER);
        JPanel linha = Componentes.painel(new BorderLayout(), "painel");
        linha.add(new SeletorQuantidade(item.quantidade(), 0, item.produto().estoque(), quantidade), BorderLayout.WEST);
        linha.add(Componentes.texto(Dinheiro.formatar(item.total()), 14, true), BorderLayout.EAST);
        dados.add(linha, BorderLayout.SOUTH); add(dados, BorderLayout.CENTER);
    }
}
