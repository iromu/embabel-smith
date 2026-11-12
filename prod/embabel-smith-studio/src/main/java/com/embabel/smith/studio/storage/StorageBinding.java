/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import com.embabel.smith.studio.storage.repository.DiscoveryRepository;
import com.embabel.smith.studio.storage.repository.PackRepository;
import com.embabel.smith.studio.storage.repository.TraceRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One bound database (change: smith-studio-postgres-storage): a Hikari connection pool, a
 * programmatically built {@code EntityManagerFactory}, the repository proxies, and a
 * transaction template. Nothing here is a Spring-managed bean — the
 * {@link StorageContext} builds a binding, verifies connectivity, atomically swaps it in,
 * and destroys the previous one on rebind.
 *
 * <p>
 * Because the repositories are created outside a Spring context, their
 * {@code @Transactional} methods carry no interceptor; every operation is therefore run
 * inside {@link #tx()}.
 *
 * @author Ivan Rodriguez
 */
public final class StorageBinding {

	private final HikariDataSource dataSource;

	private final LocalContainerEntityManagerFactoryBean emf;

	private final TransactionTemplate tx;

	private final PackRepository packs;

	private final DiscoveryRepository discoveries;

	private final TraceRepository traces;

	private StorageBinding(HikariDataSource dataSource, LocalContainerEntityManagerFactoryBean emf,
			TransactionTemplate tx, PackRepository packs, DiscoveryRepository discoveries, TraceRepository traces) {
		this.dataSource = dataSource;
		this.emf = emf;
		this.tx = tx;
		this.packs = packs;
		this.discoveries = discoveries;
		this.traces = traces;
	}

	/**
	 * Builds and verifies a binding for the endpoint. A test query runs before anything
	 * is returned; if the endpoint is unreachable (or the schema cannot be created) the
	 * partially built resources are torn down and an exception is thrown so the caller
	 * keeps its previous binding.
	 * @param settings the database endpoint to bind.
	 * @return the verified binding.
	 */
	public static StorageBinding create(StorageSettings.DbSettings settings) {
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(settings.url());
		ds.setUsername(settings.user());
		ds.setPassword(settings.password());
		ds.setMaximumPoolSize(5);
		ds.setPoolName("smith-studio-storage");
		try {
			verifyConnectivity(ds);
		}
		catch (RuntimeException ex) {
			ds.close();
			throw ex;
		}

		LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
		emfBean.setDataSource(ds);
		emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		emfBean.setPackagesToScan("com.embabel.smith.studio.storage.entity");
		Properties jpa = new Properties();
		jpa.setProperty("hibernate.hbm2ddl.auto", "update");
		emfBean.setJpaProperties(jpa);
		try {
			emfBean.afterPropertiesSet();
		}
		catch (RuntimeException ex) {
			ds.close();
			throw ex;
		}

		EntityManagerFactory emf = emfBean.getObject();
		JpaTransactionManager txManager = new JpaTransactionManager();
		txManager.setEntityManagerFactory(emf);
		txManager.afterPropertiesSet();
		TransactionTemplate tx = new TransactionTemplate(txManager);
		// A shared EntityManager proxy: each repository call joins the transaction
		// started by the
		// transaction template (bound to the same EntityManagerFactory) or opens a short
		// session.
		EntityManager sharedEm = SharedEntityManagerCreator.createSharedEntityManager(emf);
		JpaRepositoryFactory factory = new JpaRepositoryFactory(sharedEm);
		return new StorageBinding(ds, emfBean, tx, factory.getRepository(PackRepository.class),
				factory.getRepository(DiscoveryRepository.class), factory.getRepository(TraceRepository.class));
	}

	private static void verifyConnectivity(HikariDataSource ds) {
		try (Connection c = ds.getConnection();
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("SELECT 1")) {
			rs.next();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot connect to the storage database: " + ex.getMessage(), ex);
		}
	}

	/**
	 * The packs repository for this binding.
	 * @return the packs repository.
	 */
	public PackRepository packs() {
		return this.packs;
	}

	/**
	 * The discovery-history repository for this binding.
	 * @return the discovery-history repository.
	 */
	public DiscoveryRepository discoveries() {
		return this.discoveries;
	}

	/**
	 * The traces repository for this binding.
	 * @return the traces repository.
	 */
	public TraceRepository traces() {
		return this.traces;
	}

	/**
	 * The transaction template; run every repository operation inside it.
	 * @return the transaction template.
	 */
	public TransactionTemplate tx() {
		return this.tx;
	}

	/**
	 * True once the connection pool has been closed (after a rebind or shutdown).
	 * @return true once the connection pool has been closed.
	 */
	public boolean isPoolClosed() {
		return this.dataSource.isClosed();
	}

	/** Closes the connection pool and destroys the EntityManagerFactory. Idempotent. */
	public void close() {
		try {
			this.emf.destroy();
		}
		catch (RuntimeException ignored) {
			// The EMF may already be closed; the pool close below still runs.
		}
		this.dataSource.close();
	}

}
