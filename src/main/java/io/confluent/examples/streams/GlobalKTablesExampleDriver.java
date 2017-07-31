package io.confluent.examples.streams;

import io.confluent.examples.streams.avro.Customer;
import io.confluent.examples.streams.avro.EnricherOrder;
import io.confluent.examples.streams.avro.Order;
import io.confluent.examples.streams.avro.Product;
import io.confluent.examples.streams.utils.SpecificAvroSerde;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import static io.confluent.examples.streams.GlobalKTablesExample.CUSTOMER_TOPIC;
import static io.confluent.examples.streams.GlobalKTablesExample.ORDER_TOPIC;
import static io.confluent.examples.streams.GlobalKTablesExample.PRODUCT_TOPIC;

public class GlobalKTablesExampleDriver {

    private static final Random RANDOM = new Random();
    private static final int RECORDS_TO_GENERATE = 100;

    public static void main(String[] args) {
        String bootstrapServers = "192.168.9.211:9092";
        String schemaRegistryUrl = "http://localhost:8081";

        generateCustomers(bootstrapServers, schemaRegistryUrl, RECORDS_TO_GENERATE);
        generateProducts(bootstrapServers, schemaRegistryUrl, RECORDS_TO_GENERATE);
        generateOrders(bootstrapServers, schemaRegistryUrl, RECORDS_TO_GENERATE, RECORDS_TO_GENERATE,
                RECORDS_TO_GENERATE);
        receiveEnrichedOrders(bootstrapServers, schemaRegistryUrl, RECORDS_TO_GENERATE);
    }

    private static void receiveEnrichedOrders(String bootstrapServers, String schemaRegistryUrl, int expected) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "global-tables-consumer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, Serdes.Long().deserializer().getClass());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        final KafkaConsumer<Long, EnricherOrder> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singleton(GlobalKTablesExample.ENRICHED_ORDER_TOPIC));
        int received = 0;
        while (received < expected) {
            final ConsumerRecords<Long, EnricherOrder> records = consumer.poll(Long.MAX_VALUE);
            records.forEach(record ->
                System.out.println("Key : " + record.key() + ", Value : " + record.value())
            );
        }
        consumer.close();
    }

    private static List<Order> generateOrders(String bootstrapServers, String schemaRegistryUrl, int numCustomers, int numProducts, int count) {

        final Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final SpecificAvroSerde<Order> ordersSerde = createSerde(schemaRegistryUrl);
        final KafkaProducer<Long, Order> producer =
                new KafkaProducer<>(producerProperties, Serdes.Long().serializer(), ordersSerde.serializer());

        final List<Order> allOrders = new ArrayList<>();
        for(long i = 0; i < count; i++) {
            final long customerId = RANDOM.nextInt(numCustomers);
            final long productId = RANDOM.nextInt(numProducts);
            final Order order = new Order(customerId, productId, RANDOM.nextLong());
            allOrders.add(order);
            producer.send(new ProducerRecord<>(ORDER_TOPIC, i, order));
        }
        producer.close();
        return allOrders;
    }

    private static List<Product> generateProducts(String bootstrapServers, String schemaRegistryUrl, int count) {
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final SpecificAvroSerde<Product> productSerde = createSerde(schemaRegistryUrl);
        final KafkaProducer<Long, Product> producer = new KafkaProducer<>(properties, Serdes.Long().serializer(), productSerde.serializer());

        List<Product> allproducts = new ArrayList<>();
        for (long i=0; i<count; i++) {
            final Product product = new Product(randomString(10), randomString(count), randomString(20));
            allproducts.add(product);
            producer.send(new ProducerRecord<>(PRODUCT_TOPIC, i, product));
        }
        return allproducts;
    }

    private static List<Customer> generateCustomers(String bootstrapServers, String schemaRegistryUrl, int count) {
        final SpecificAvroSerde<Customer> customerSerde = createSerde(schemaRegistryUrl);
        final Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final KafkaProducer<Long, Customer>
                customerProducer =
                new KafkaProducer<>(producerProperties, Serdes.Long().serializer(), customerSerde.serializer());
        final List<Customer> allCustomers = new ArrayList<>();
        final String [] genders = {"male", "female", "unknown"};
        final Random random = new Random();
        for(long i = 0; i < count; i++) {
            final Customer customer = new Customer(randomString(10),
                    genders[random.nextInt(genders.length)],
                    randomString(20));
            allCustomers.add(customer);
            customerProducer.send(new ProducerRecord<>(CUSTOMER_TOPIC, i, customer));
        }
        customerProducer.close();
        return allCustomers;
    }

    private static <VT extends SpecificRecord> SpecificAvroSerde<VT> createSerde(final String schemaRegistryUrl) {
        final CachedSchemaRegistryClient schemaRegistry = new CachedSchemaRegistryClient(schemaRegistryUrl, 100);
        final Map<String, String> serdeProps = Collections.singletonMap("schema.registry.url", schemaRegistryUrl);

        final SpecificAvroSerde<VT> serde = new SpecificAvroSerde<>(schemaRegistry, serdeProps);
        serde.configure(serdeProps, true);
        return serde;
    }

    private static String randomString(int len) {
        final StringBuilder b = new StringBuilder();

        for(int i = 0; i < len; ++i) {
            b.append("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".charAt(RANDOM.nextInt
                    ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".length())));
        }

        return b.toString();
    }

}
