package com.github.j0rdanit0.service;

import com.github.j0rdanit0.domain.Book;
import com.github.j0rdanit0.domain.BookRequest;

import java.util.*;

public class BookService
{
    public static final Map<UUID, Book> BOOKS = new HashMap<>();

    public static List<Book> getBooks( String name, String author )
    {
        List<Book> books = new ArrayList<>( BOOKS.values() );

        Optional.ofNullable( name )
                .ifPresent( x -> books.removeIf( book -> !book.getName().toUpperCase().contains( name.toUpperCase() ) ) );

        Optional.ofNullable( author )
                .ifPresent( x -> books.removeIf( book -> !book.getAuthor().toUpperCase().contains( author.toUpperCase() ) ) );

        return books;
    }

    public static Book createBook( BookRequest bookRequest )
    {
        Book book = new Book( bookRequest );
        book.setId( UUID.randomUUID() );

        BOOKS.put( book.getId(), book );

        return book;
    }

    public static Book getBook( UUID id )
    {
        return BOOKS.get( id );
    }

    public static Book editBook( UUID id, BookRequest bookRequest )
    {
        Book book = BOOKS.get( id );
        if ( book != null )
        {
            Optional.ofNullable( bookRequest.getName() )
                    .ifPresent( book::setName );

            Optional.ofNullable( bookRequest.getAuthor() )
                    .ifPresent( book::setAuthor );

            BOOKS.put( id, book );
        }

        return book;
    }

    public static Book removeBook( UUID id )
    {
        return BOOKS.remove( id );
    }
}
