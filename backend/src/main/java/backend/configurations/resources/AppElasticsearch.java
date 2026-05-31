package backend.configurations.resources;

import backend.configurations.environment.EnvironmentSetting;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppElasticsearch {

    private final EnvironmentSetting env;

    public AppElasticsearch(EnvironmentSetting env) {
        this.env = env;
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        EnvironmentSetting.Elasticsearch es = env.getElasticsearch();

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(es.getHost(), es.getPort(), es.getScheme()));

        if (!es.getApiKey().isBlank()) {
            builder.setDefaultHeaders(new org.apache.http.Header[]{
                    new BasicHeader("Authorization", "ApiKey " + es.getApiKey())
            });
        } else if (!es.getUsername().isBlank()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword()));
            builder.setHttpClientConfigCallback(
                    httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        RestClientTransport transport = new RestClientTransport(builder.build(), new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
