package org.folio.circulation.pact;

import static io.pactfoundation.consumer.dsl.LambdaDsl.newJsonBody;


import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.PactProviderRuleMk2;
import au.com.dius.pact.consumer.PactVerification;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.model.RequestResponsePact;


import org.junit.Rule;
import org.junit.Test;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;


import org.folio.circulation.domain.Item;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


@RunWith(VertxUnitRunner.class)
public class ItemPactTest {
	
	private Map<String, String> okapiHeaders = new HashMap<>();
	//private Field mock = null;
	 private Vertx vertx;
	 private Router router;

    @Rule
    public PactProviderRuleMk2 provider = new PactProviderRuleMk2("item-storage", this);
   
    
    @Before
    public void setUp(TestContext context) throws Exception {
      vertx = Vertx.vertx();  
      router = Router.router(vertx);
    }

    @After
    public void after(TestContext context) {
      vertx.close(context.asyncAssertSuccess());
    }


    @Pact(provider = "item-storage", consumer = "mod-circulation")
    public RequestResponsePact createPact(PactDslWithProvider builder) throws IOException {
        
    	final Map<String, String> headers = new HashMap<>();
        headers.put("x-okapi-tenant", "tenant");
        headers.put("x-okapi-token", "token");
        
        // expected  Pact
		final DslPart itemBody = newJsonBody(o -> {
			o.numberValue("totalResults", 1).array("items", item -> {
				item.object(prop -> {
					prop.stringType("id", "holdingsRecordId", "barcode", "enumeration", "materialTypeId",
							"permanentLocationId", "temporaryLocationId", "permanentLoanTypeId", "temporaryLoanTypeId")
							.object("status", stat -> {
								stat.stringType("name");
							});
				});
			});
		}).build();
 	
    	return builder
            .given("Items list")
            .uponReceiving("request for an item")
            .path("/item-storage/items/1234" )
            .method("GET")
            .headers(headers)
            .willRespondWith()
            .status(200)
            .body(itemBody)
            .toPact();
    }

     @Test
     @PactVerification("item-storage")
    public void pactTest(TestContext context) {
 		 
    	 final Async async = context.async();
    	 router.route("/circulation/loans").handler(routingContext  ->  {
   
      	   WebContext webContext  = new WebContext(routingContext);
      	   Clients clients= Clients.create(webContext, vertx.createHttpClient());
      	   ItemRepository itemRepository = new ItemRepository(clients, true, true);      
           CompletableFuture<HttpResult<Item>> result = itemRepository.fetchById("1234");
           result.whenComplete((item,throwable)-> {
          	  context.assertNotNull(item.value().getItemId());
          	  context.assertNotNull(item.value().getStatus());
          	  context.assertNotNull(item.value().getBarcode());
          	  async.complete();
              }).exceptionally(throwable -> {
                  context.fail(throwable);
                  async.complete();
                  return null;
                });  	 
    	 });        
     }
}
