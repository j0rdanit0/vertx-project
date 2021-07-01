package com.github.j0rdanit0.verticle;

import com.github.j0rdanit0.domain.Book;
import com.github.j0rdanit0.domain.BookRequest;
import com.github.j0rdanit0.service.BookService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class BookListener extends AbstractVerticle
{
    private final Logger logger = LoggerFactory.getLogger( BookListener.class );

    @Override
    public void start()
    {
        EventBus bus = vertx
          .eventBus()
          .registerDefaultCodec( BookRequest.class, new BookRequest.Codec() )
          .registerDefaultCodec( Book.class, new Book.Codec() );

        bus.localConsumer( "get.books", this::getBooks );
        bus.localConsumer( "create.book", this::createBook );
        bus.localConsumer( "get.book", this::getBook );
        bus.localConsumer( "edit.book", this::editBook );
        bus.localConsumer( "remove.book", this::removeBook );
    }

    private void getBooks( Message<BookRequest> message )
    {
        logger.info( "Get books (listener)" );
        BookRequest request = message.body();
        message.reply( Json.encodeToBuffer( BookService.getBooks( request.getName(), request.getAuthor() ) ) );
    }

    private void createBook( Message<BookRequest> message )
    {
        logger.info( "Create book [" + message.body() + "] (listener)" );
        message.reply( Json.encodeToBuffer( BookService.createBook( message.body() ) ) );
    }

    private void getBook( Message<String> message )
    {
        logger.info( "Get book (listener)" );
        message.reply( Json.encodeToBuffer( BookService.getBook( UUID.fromString( message.body() ) ) ) );
    }

    private void editBook( Message<JsonObject> message )
    {
        logger.info( "Edit book (listener)" );
        JsonObject bookRequest = message.body().getJsonObject( "bookRequest" );
        BookRequest request = new BookRequest( bookRequest.getString( "name" ), bookRequest.getString( "author" ) );
        message.reply( Json.encodeToBuffer( BookService.editBook( UUID.fromString( message.body().getString( "id" ) ), request ) ) );
    }

    private void removeBook( Message<String> message )
    {
        logger.info( "Remove book (listener)" );
        message.reply( Json.encodeToBuffer( BookService.removeBook( UUID.fromString( message.body() ) ) ) );
    }
}
