module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires org.apache.commons.lang3;
    requires com.google.common;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.spi;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires dagger;
    requires javax.inject;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app.service.token.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.entity to
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app.service.token.impl.test,
            com.hedera.node.app;

    opens com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app.service.token.impl.test;

    exports com.hedera.node.app.service.token.impl.components;
    exports com.hedera.node.app.service.token.impl.serdes;
}
