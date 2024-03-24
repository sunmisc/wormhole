package sunmisc.utils.concurrent.deque;

interface Node<E> {

    E item();

    boolean tryDelete();


    void updateNext();

    void updatePrev();


    Node<E> next();

    Node<E> prev();

}
