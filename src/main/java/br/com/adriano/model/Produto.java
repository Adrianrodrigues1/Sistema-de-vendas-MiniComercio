package br.com.adriano.model;

import java.math.BigDecimal;

/**
 * Modelo de produto - compatível com v1.0 e estendido para v2.0.
 * <p>
 * Campos legados: id, nome, preco (Double), estoque
 * Campos v2.0: codigo, categoria, imagem, ativo, preco (BigDecimal)
 */
public class Produto {

    private Integer id;
    private String codigo;
    private String nome;
    private String categoria;
    private BigDecimal preco;        // v2.0: BigDecimal para precisão monetária
    private Double precoLegado;      // v1.0: compatibilidade
    private Integer estoque;
    private String imagem;
    private boolean ativo = true;

    // Construtores legados (compatibilidade v1.0)
    public Produto() { }

    public Produto(String nome, Double preco, Integer estoque) {
        this.nome = nome;
        this.precoLegado = preco;
        this.preco = preco != null ? BigDecimal.valueOf(preco) : BigDecimal.ZERO;
        this.estoque = estoque;
    }

    public Produto(Integer id, String nome, Double preco, Integer estoque) {
        this.id = id;
        this.nome = nome;
        this.precoLegado = preco;
        this.preco = preco != null ? BigDecimal.valueOf(preco) : BigDecimal.ZERO;
        this.estoque = estoque;
    }

    // Construtor v2.0 completo
    public Produto(Integer id, String codigo, String nome, String categoria, BigDecimal preco, Integer estoque, String imagem, boolean ativo) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.categoria = categoria;
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.precoLegado = this.preco.doubleValue();
        this.estoque = estoque != null ? estoque : 0;
        this.imagem = imagem;
        this.ativo = ativo;
    }

    // Getters/Setters legados
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Double getPreco() { return precoLegado; }
    public void setPreco(Double preco) {
        this.precoLegado = preco;
        this.preco = preco != null ? BigDecimal.valueOf(preco).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public Integer getEstoque() { return estoque; }
    public void setEstoque(Integer estoque) { this.estoque = estoque; }

    // Getters/Setters v2.0
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public BigDecimal getPrecoBigDecimal() { return preco; }
    public void setPrecoBigDecimal(BigDecimal preco) {
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.precoLegado = this.preco.doubleValue();
    }

    public String getImagem() { return imagem; }
    public void setImagem(String imagem) { this.imagem = imagem; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    // Compatibilidade: setter para preco como BigDecimal
    public void setPreco(BigDecimal preco) { setPrecoBigDecimal(preco); }
}