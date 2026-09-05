package br.com.adriano.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class CriadorTabelas {

    public static void criarTabelas() {
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "usuario TEXT UNIQUE NOT NULL, "
                + "senha TEXT NOT NULL"
                + ");";

        String sqlProdutos = "CREATE TABLE IF NOT EXISTS produtos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nome TEXT NOT NULL, "
                + "preco REAL NOT NULL, "
                + "estoque INTEGER NOT NULL"
                + ");";

        String sqlVendas = "CREATE TABLE IF NOT EXISTS vendas ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "data TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "forma_pagamento TEXT NOT NULL, "
                + "total REAL NOT NULL"
                + ");";

        String sqlItensVenda = "CREATE TABLE IF NOT EXISTS itens_venda ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "venda_id INTEGER NOT NULL, "
                + "produto_id INTEGER NOT NULL, "
                + "quantidade INTEGER NOT NULL, "
                + "preco_unitario REAL NOT NULL, "
                + "FOREIGN KEY (venda_id) REFERENCES vendas(id), "
                + "FOREIGN KEY (produto_id) REFERENCES produtos(id)"
                + ");";

        String sqlCaixa = "CREATE TABLE IF NOT EXISTS caixa ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "valor_inicial REAL NOT NULL, "
                + "status TEXT NOT NULL, "
                + "data_abertura TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "data_fechamento TEXT"
                + ");";

        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlUsuarios);
            stmt.execute(sqlProdutos);
            stmt.execute(sqlVendas);
            stmt.execute(sqlItensVenda);
            stmt.execute(sqlCaixa);

            // Usuario padrao para login inicial (usuario: admin | senha: 1234)
            String sqlAdmin = "INSERT OR IGNORE INTO usuarios (id, usuario, senha) VALUES (1, 'admin', '1234');";
            stmt.execute(sqlAdmin);

            System.out.println("Tabelas inicializadas com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao inicializar banco de dados: " + e.getMessage());
        }
    }
}