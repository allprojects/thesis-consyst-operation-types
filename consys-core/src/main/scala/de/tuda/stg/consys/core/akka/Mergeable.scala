package de.tuda.stg.consys.core.akka

trait Mergeable[T]{
  private[akka] def merge(other:T)

}
