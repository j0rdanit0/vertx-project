package com.github.j0rdanit0.domain;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class Book
{
    private UUID id = UUID.randomUUID();

    private String name;
    private String author;

    public Book( BookRequest bookRequest )
    {
        this( bookRequest.getName(), bookRequest.getAuthor() );
    }

    public Book( String name, String author )
    {
        this.name = name;
        this.author = author;
    }

    public static class Codec implements MessageCodec<Book, Book>
    {
        @Override
        public void encodeToWire( Buffer buffer, Book book )
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put( "id", book.getId().toString() );
            jsonObject.put( "name", book.getName() );
            jsonObject.put( "author", book.getAuthor() );

            String json = jsonObject.encode();

            buffer.appendString( json );
        }

        @Override
        public Book decodeFromWire( int pos, Buffer buffer )
        {
            JsonObject jsonObject = buffer.toJsonObject();

            UUID id = UUID.fromString( jsonObject.getString( "id" ) );
            String name = jsonObject.getString( "name" );
            String author = jsonObject.getString( "author" );

            Book book = new Book( name, author );
            book.setId( id );
            return book;
        }

        @Override
        public Book transform( Book book )
        {
            return book;
        }

        @Override
        public String name()
        {
            return this.getClass().getCanonicalName();
        }

        @Override
        public byte systemCodecID()
        {
            return -1;
        }
    }
}
