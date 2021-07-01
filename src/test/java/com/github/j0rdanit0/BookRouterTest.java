package com.github.j0rdanit0;

import com.github.j0rdanit0.domain.Book;
import com.github.j0rdanit0.domain.BookRequest;
import com.github.j0rdanit0.service.BookService;
import com.github.j0rdanit0.verticle.BookListener;
import com.github.j0rdanit0.verticle.BookRouter;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith( VertxExtension.class )
public class BookRouterTest
{
    private static final Logger logger = LoggerFactory.getLogger( BookRouterTest.class );

    //saving config to the injected Vertx instance with JsonObject#mergeIn works inside the scope of the @BeforeAll method,
    //but it doesn't get propagated to each test method. This static variable is a workaround for that.
    private static JsonObject config;

    @BeforeAll
    public static void beforeAll( Vertx vertx, VertxTestContext testContext )
    {
        populateBooks();

        Vertx configVertx = Vertx.vertx();

        ConfigRetrieverOptions options = new ConfigRetrieverOptions();
        options.addStore( new ConfigStoreOptions().setType( "file" ).setConfig( new JsonObject().put( "path", "config.json" ) ) );

        ConfigRetriever
          .create( configVertx, options )
          .getConfig( json -> {
            if ( json.succeeded() )
            {
                config = json.result();
                logger.info( "Loaded config: " + config );

                DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig( config );
                vertx.deployVerticle( BookRouter.class.getName(), deploymentOptions, testContext.succeedingThenComplete() );
                vertx.deployVerticle( BookListener.class.getName(), deploymentOptions, testContext.succeedingThenComplete() );
            }
            else
            {
                logger.error( "Unable to load config." );
            }

            configVertx.close();
            testContext.completeNow();
        });
    }

    private static void populateBooks()
    {
        Map.of(
          "Harry Potter and the Sorcerer's Stone", "J. K. Rowling",
          "Harry Potter and the Chamber of Secrets", "J. K. Rowling",
          "Harry Potter and the Prisoner of Azkaban", "J. K. Rowling",
          "The Hobbit", "J. R. R. Tolkien",
          "The Lion, the Witch, and the Wardrobe", "C. S. Lewis",
          "The Very Hungry Caterpillar", "Eric Carle",
          "The 7 Habits of Highly Effective People", "Stephen R. Covey"
        )
           .entrySet()
           .stream()
           .map( entry -> new Book( entry.getKey(), entry.getValue() ) )
           .forEach( book -> BookService.BOOKS.put( book.getId(), book ) );
    }

    @Test
    public void testAllTogether( Vertx vertx, VertxTestContext testContext )
    {
        WebClientOptions options = new WebClientOptions().setDefaultPort( config.getInteger( "port" ) );
        WebClient webClient = WebClient.create( vertx, options );

        int originalSize = BookService.BOOKS.size();

        createBook( webClient, "change", "me" )
          .compose( response -> editBook( webClient, response.bodyAsJson( Book.class ), "Now What?", "Charles D. Morgan" ) )
          .compose( response -> getBook( webClient, response.bodyAsJson( Book.class ).getId() ) )
          .compose( response -> removeBook( webClient, response.bodyAsJson( Book.class ).getId() ) )
          .compose( response -> getAllBooks( webClient ) )
          .onComplete( testContext.succeeding( response -> {
              assertThat( response.bodyAsJsonArray().size(), is( originalSize ) );
              testContext.completeNow();
          }));
    }

    private Future<HttpResponse<Buffer>> createBook( WebClient webClient, String name, String author )
    {
        return webClient.post( buildRequestURI() ).sendJson( new BookRequest( name, author ) );
    }

    private Future<HttpResponse<Buffer>> editBook( WebClient webClient, Book book, String name, String author )
    {
        Book existingBook = BookService.getBook( book.getId() );
        assertThat( existingBook != null, is( true ) );

        return webClient.put( buildRequestURI( book.getId().toString() ) ).sendJson( new BookRequest( name, author ) );
    }

    private Future<HttpResponse<Buffer>> getBook( WebClient webClient, UUID id )
    {
        Book book = BookService.getBook( id );
        assertThat( book != null, is( true ) );
        assertThat( book.getName(), is( "Now What?" ) );
        assertThat( book.getAuthor(), is( "Charles D. Morgan" ) );

        return webClient.get( buildRequestURI( id.toString() ) ).send();
    }

    private Future<HttpResponse<Buffer>> removeBook( WebClient webClient, UUID id )
    {
        return webClient.delete( buildRequestURI( id.toString() ) ).send();
    }

    private Future<HttpResponse<Buffer>> getAllBooks( WebClient webClient )
    {
        return webClient.get( buildRequestURI() ).send();
    }

    public static Stream<Arguments> getBooksTestData()
    {
        return Stream.of(
          Arguments.of( null, null, 200, true ),
          Arguments.of( "Harry Potter", null, 200, true ),
          Arguments.of( null, "J. K. Rowling", 200, true ),
          Arguments.of( "Harry Potter", "J. K. Rowling", 200, true ),
          Arguments.of( "The Hobbit", "J. K. Rowling", 200, false )
        );
    }

    @ParameterizedTest
    @MethodSource( "getBooksTestData" )
    public void testGetBooks( String name, String author, int expectedStatusCode, boolean expectedResults, Vertx vertx, VertxTestContext testContext )
    {
        HttpRequest<Buffer> request = WebClient
          .create( vertx )
          .get( buildRequestURI() )
          .port( config.getInteger( "port" ) );

        Optional.ofNullable( name ).ifPresent( x -> request.addQueryParam( "name", name ) );
        Optional.ofNullable( author ).ifPresent( x -> request.addQueryParam( "author", author ) );

        request.send( testContext.succeeding( response -> {
            assertThat( response.statusCode(), is( expectedStatusCode ) );

            JsonArray jsonArray = response.bodyAsJsonArray();
            assertThat( !jsonArray.isEmpty(), is( expectedResults ) );

            testContext.completeNow();
        } ) );
    }

    public static Stream<Arguments> createBookTestData()
    {
        return Stream.of(
          Arguments.of( null, null, 400 ),
          Arguments.of( null, "author", 400 ),
          Arguments.of( "name", null, 400 ),
          Arguments.of( "Harry Potter and the Goblet of Fire", "J. K. Rowling", 200 )
        );
    }

    @ParameterizedTest
    @MethodSource( "createBookTestData" )
    public void testCreateBook( String name, String author, int expectedStatusCode, Vertx vertx, VertxTestContext testContext )
    {
        WebClient
          .create( vertx )
          .post( buildRequestURI() )
          .port( config.getInteger( "port" ) )
          .sendJson( new BookRequest( name, author ), testContext.succeeding( response -> {
              assertThat( response.statusCode(), is( expectedStatusCode ) );

              if ( expectedStatusCode == 200 )
              {
                  Book book = response.bodyAsJson( Book.class );
                  assertThat( book.getId() != null, is( true ) );
                  assertThat( book.getName(), is( name ) );
                  assertThat( book.getAuthor(), is( author ) );
              }

              testContext.completeNow();
          } ) );
    }

    public static Stream<Arguments> getBookTestData()
    {
        return Stream.of(
          Arguments.of( null, 400 ),
          Arguments.of( "not-a-UUID", 400 ),
          Arguments.of( UUID.randomUUID().toString(), 404 ),
          Arguments.of( findBookIdByAuthor( "Eric Carle" ).toString(), 200 )
        );
    }

    @ParameterizedTest
    @MethodSource( "getBookTestData" )
    public void testGetBook( String id, int expectedStatusCode, Vertx vertx, VertxTestContext testContext )
    {
        WebClient
          .create( vertx )
          .get( buildRequestURI( id ) )
          .port( config.getInteger( "port" ) )
          .send( testContext.succeeding( response -> {
              assertThat( response.statusCode(), is( expectedStatusCode ) );

              if ( expectedStatusCode == 200 )
              {
                  Book responseBook = response.bodyAsJson( Book.class );
                  Book expectedBook = BookService.BOOKS.get( UUID.fromString( id ) );

                  assertThat( expectedBook != null, is( true ) );
                  assertThat( responseBook != null, is( true ) );
                  assertThat( responseBook.getId().toString(), is( id ) );
                  assertThat( responseBook.getId(), is( expectedBook.getId() ) );
                  assertThat( responseBook.getName(), is( expectedBook.getName() ) );
                  assertThat( responseBook.getAuthor(), is( expectedBook.getAuthor() ) );
              }

              testContext.completeNow();
          } ) );
    }

    public static Stream<Arguments> editBookTestData()
    {
        return Stream.of(
          Arguments.of( null, null, null, 400 ),
          Arguments.of( "not-a-UUID", null, null, 400 ),
          Arguments.of( UUID.randomUUID().toString(), null, null, 400 ),
          Arguments.of( UUID.randomUUID().toString(), "name", null, 404 ),
          Arguments.of( findBookIdByAuthor( "C. S. Lewis" ).toString(), null, "Clive Staples Lewis", 200 ),
          Arguments.of( findBookIdByName( "Sorcerer's Stone" ).toString(), "Harry Potter and the Philosopher's Stone", "Joanne Rowling", 200 )
        );
    }

    @ParameterizedTest
    @MethodSource( "editBookTestData" )
    public void testEditBook( String id, String name, String author, int expectedStatusCode, Vertx vertx, VertxTestContext testContext )
    {
        WebClient
          .create( vertx )
          .put( buildRequestURI( id ) )
          .port( config.getInteger( "port" ) )
          .sendJson( new BookRequest( name, author ), testContext.succeeding( response -> {
              assertThat( response.statusCode(), is( expectedStatusCode ) );

              if ( expectedStatusCode == 200 )
              {
                  Book responseBook = response.bodyAsJson( Book.class );
                  Book expectedBook = BookService.BOOKS.get( UUID.fromString( id ) );

                  assertThat( expectedBook != null, is( true ) );
                  assertThat( responseBook != null, is( true ) );
                  assertThat( responseBook.getId().toString(), is( id ) );
                  assertThat( responseBook.getId(), is( expectedBook.getId() ) );
                  assertThat( responseBook.getName(), is( expectedBook.getName() ) );
                  assertThat( responseBook.getAuthor(), is( expectedBook.getAuthor() ) );
              }

              testContext.completeNow();
          } ) );
    }

    public static Stream<Arguments> removeBookTestData()
    {
        return Stream.of(
          Arguments.of( null, 400 ),
          Arguments.of( "not-a-UUID", 400 ),
          Arguments.of( UUID.randomUUID().toString(), 404 ),
          Arguments.of( findBookIdByName( "7 Habits" ).toString(), 200 )
        );
    }

    @ParameterizedTest
    @MethodSource( "removeBookTestData" )
    public void testRemoveBook( String id, int expectedStatusCode, Vertx vertx, VertxTestContext testContext )
    {
        WebClient
          .create( vertx )
          .delete( buildRequestURI( id ) )
          .port( config.getInteger( "port" ) )
          .send( testContext.succeeding( response -> {
              assertThat( response.statusCode(), is( expectedStatusCode ) );

              if ( expectedStatusCode == 200 )
              {
                  assertThat( BookService.BOOKS.get( UUID.fromString( id ) ) == null, is( true ) );
              }

              testContext.completeNow();
          } ) );
    }

    private static UUID findBookIdByName( String name )
    {
        return findBookId( book -> book.getName().contains( name ) );
    }

    private static UUID findBookIdByAuthor( String author )
    {
        return findBookId( book -> book.getAuthor().contains( author ) );
    }

    private static UUID findBookId( Predicate<Book> predicate )
    {
        return BookService.BOOKS
          .values()
          .stream()
          .filter( predicate )
          .findFirst()
          .orElseThrow()
          .getId();
    }

    private String buildRequestURI( String... pathParts )
    {
        return config.getString( "apiBase" ) +
               "/books/" +
               String.join( "/", pathParts );
    }
}
