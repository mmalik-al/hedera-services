module com.hedera.node.app.spi {
    requires transitive com.hedera.hashgraph.protobuf.java.api;
    requires static transitive com.github.spotbugs.annotations;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires com.swirlds.common;
    requires com.google.protobuf;
    requires com.swirlds.config;
    requires com.hedera.hashgraph.pbj.runtime;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;
    exports com.hedera.node.app.spi.exceptions;

    opens com.hedera.node.app.spi to
            com.hedera.node.app.service.mono.testFixtures;
    opens com.hedera.node.app.spi.workflows to
            com.hedera.node.app.service.mono.testFixtures;

    exports com.hedera.node.app.spi.state.serdes;
    exports com.hedera.node.app.spi.config;
    exports com.hedera.node.app.spi.records;
    exports com.hedera.node.app.spi.validation;
    exports com.hedera.node.app.spi.accounts;

    opens com.hedera.node.app.spi.accounts to
            com.hedera.node.app.service.mono.testFixtures,
            com.hedera.node.app.spi.test;
}
