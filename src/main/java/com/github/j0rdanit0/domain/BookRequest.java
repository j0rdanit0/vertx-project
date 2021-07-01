package com.github.j0rdanit0.domain;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookRequest
{
    private String name;
    private String author;

    public static class Codec implements MessageCodec<BookRequest, BookRequest>
    {
        @Override
        public void encodeToWire( Buffer buffer, BookRequest bookRequest )
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put( "name", bookRequest.getName() );
            jsonObject.put( "author", bookRequest.getAuthor() );

            String json = jsonObject.encode();

            buffer.appendString( json );
        }

        @Override
        public BookRequest decodeFromWire( int pos, Buffer buffer )
        {
            JsonObject jsonObject = buffer.toJsonObject();

            String name = jsonObject.getString( "name" );
            String author = jsonObject.getString( "author" );

            return new BookRequest( name, author );
        }

        @Override
        public BookRequest transform( BookRequest bookRequest )
        {
            return bookRequest;
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
