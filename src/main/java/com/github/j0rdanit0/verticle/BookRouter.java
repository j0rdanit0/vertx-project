package com.github.j0rdanit0.verticle;

import com.github.j0rdanit0.domain.BookRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class BookRouter extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger( BookRouter.class );

    @Override
    public void start( Promise<Void> promise )
    {
        vertx
          .createHttpServer()
          .requestHandler( getRouter() )
          .listen( vertx.getOrCreateContext().config().getInteger( "port", 8080 ), result -> handleHttpServerListen( result, promise ) );
    }

    private void handleHttpServerListen( AsyncResult<HttpServer> result, Promise<Void> promise )
    {
        if ( result.succeeded() )
        {
            promise.complete();
        }
        else
        {
            promise.fail( result.cause() );
        }
    }

    public Router getRouter()
    {
        Router router = Router.router( vertx );

        router
          .get( buildRequestURI() )
          .handler( this::getBooks );

        long requestBodyLimit = vertx.getOrCreateContext().config().getLong( "requestBodyLimit", 1_000L );
        router
          .post( buildRequestURI() )
          .handler( BodyHandler.create().setBodyLimit( requestBodyLimit ) )
          .handler( this::createBook );

        router
          .get( buildRequestURI( ":id" ) )
          .handler( this::getBook );

        router
          .put( buildRequestURI( ":id" ) )
          .handler( BodyHandler.create().setBodyLimit( requestBodyLimit ) )
          .handler( this::editBook );

        router
          .delete( buildRequestURI( ":id" ) )
          .handler( this::removeBook );

        return router;
    }

    public String buildRequestURI( String... pathParts )
    {
        return vertx.getOrCreateContext().config().getString( "apiBase", "" ) +
               "/books/" +
               String.join( "/", pathParts );
    }

    private void getBooks( RoutingContext context )
    {
        logger.info( "Get books (router)" );
        BookRequest request = new BookRequest( context.request().getParam( "name" ), context.request().getParam( "author" ) );

        vertx
          .eventBus()
          .<Buffer>request( "get.books", request, result -> handleEventBusReplyByArray( result, context.response() ) );
    }

    private void createBook( RoutingContext context )
    {
        BookRequest bookRequest = context.getBodyAsJson().mapTo( BookRequest.class );
        logger.info( "Create book [" + bookRequest + "] (router)" );

        if ( bookRequest.getName() == null || bookRequest.getAuthor() == null )
        {
            logger.warn( "All fields are required." );
            context
              .response()
              .setStatusCode( 400 )
              .end( "All fields are required." );
        }
        else
        {
            vertx
              .eventBus()
              .<Buffer>request( "create.book", bookRequest, result -> handleEventBusReplyByObject( result, context.response() ) );
        }
    }

    private void getBook( RoutingContext context )
    {
        logger.info( "Get book (router)" );
        doWithPathId( context, id -> {
            vertx
              .eventBus()
              .<Buffer>request( "get.book", id.toString(), result -> handleEventBusReplyByObject( result, context.response() ) );
        }, "Unable to get book" );
    }

    private void editBook( RoutingContext context )
    {
        BookRequest bookRequest = context.getBodyAsJson().mapTo( BookRequest.class );
        logger.info( "Edit book [" + bookRequest + "] (router)" );

        doWithPathId( context, id -> {
            if ( bookRequest.getName() == null && bookRequest.getAuthor() == null )
            {
                logger.warn( "At least one field is required." );
                context
                  .response()
                  .setStatusCode( 400 )
                  .end( "At least one field is required." );
            }
            else
            {
                JsonObject message = new JsonObject()
                  .put( "id", id.toString() )
                  .put( "bookRequest", new JsonObject()
                    .put( "name", bookRequest.getName() )
                    .put( "author", bookRequest.getAuthor() ) );

                vertx
                  .eventBus()
                  .<Buffer>request( "edit.book", message, result -> handleEventBusReplyByObject( result, context.response() ) );
            }
        }, "Unable to edit book [" + bookRequest + "]" );
    }

    private void removeBook( RoutingContext context )
    {
        logger.info( "Remove book (router)" );

        doWithPathId( context, id -> {
            vertx
              .eventBus()
              .<Buffer>request( "remove.book", id.toString(), result -> handleEventBusReplyByObject( result, context.response() ) );
        }, "Unable to remove book" );
    }

    private void handleEventBusReplyByArray( AsyncResult<Message<Buffer>> result, HttpServerResponse response )
    {
        handleEventBusReply( result, response, Buffer::toJsonArray );
    }

    private void handleEventBusReplyByObject( AsyncResult<Message<Buffer>> result, HttpServerResponse response )
    {
        handleEventBusReply( result, response, Buffer::toJsonObject );
    }

    private void handleEventBusReply( AsyncResult<Message<Buffer>> result, HttpServerResponse response, Function<Buffer, Object> encoder )
    {
        if ( result.succeeded() )
        {
            Buffer body = result.result().body();
            if ( body.toString().equals( "null" ) )
            {
                response.setStatusCode( 404 ).end();
            }
            else
            {
                response.end( Json.encode( encoder.apply( body ) ) );
            }
        }
        else
        {
            logger.error( "Unable to handle event bus response", result.cause() );
            response.end();
        }
    }

    private void doWithPathId( RoutingContext context, Consumer<UUID> idConsumer, String errorMessage )
    {
        Optional<String> requestId = Optional.ofNullable( context.pathParam( "id" ) );
        if ( requestId.isPresent() )
        {
            try
            {
                UUID id = UUID.fromString( requestId.get() );

                idConsumer.accept( id );
            }
            catch ( IllegalArgumentException exception )
            {
                errorMessage += ", invalid ID: [" + requestId.get() + "]";
                logger.warn( errorMessage );
                context
                  .response()
                  .setStatusCode( 400 )
                  .end( errorMessage );
            }
        }
        else
        {
            errorMessage += ", ID is required";
            logger.warn( errorMessage );
            context
              .response()
              .setStatusCode( 400 )
              .end( errorMessage );
        }
    }
}
