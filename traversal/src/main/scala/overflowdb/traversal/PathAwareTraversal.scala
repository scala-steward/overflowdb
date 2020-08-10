package overflowdb.traversal

import scala.collection.{IterableOnce, Iterator}

class PathAwareTraversal[A](val elementsWithPath: IterableOnce[(A, Vector[Any])])
  extends Traversal[A](elementsWithPath.map(_._1)) {

  override def flatMap[B](f: A => IterableOnce[B]): Traversal[B] =
    new PathAwareTraversal(
      elementsWithPath.flatMap { case (a, path) =>
        f(a).map(b => (b, path.appended(a)))
      }
    )

  override def map[B](f: A => B): Traversal[B] =
    new PathAwareTraversal(
      elementsWithPath.map { case (a, path) =>
        val b = f(a)
        (b, path.appended(b))
      }
    )

  override def collect[B](pf: PartialFunction[A, B]): Traversal[B] =
    new PathAwareTraversal(
      elementsWithPath.collect { case (a, path) if pf.isDefinedAt(a) =>
        val b = pf(a)
        (b, path.appended(a))}
    )

  override def filter(pred: A => Boolean): Traversal[A] =
    new PathAwareTraversal(
      elementsWithPath.filter(x => pred(x._1))
    )

  override def filterNot(pred: A => Boolean): Traversal[A] =
    new PathAwareTraversal(
      elementsWithPath.filterNot(x => pred(x._1))
    )

  override def dedup(implicit behaviourBuilder: DedupBehaviour.Builder => DedupBehaviour.Builder): Traversal[A] =
    new PathAwareTraversal(
      behaviourBuilder(new DedupBehaviour.Builder).build.comparisonStyle match {
        case DedupBehaviour.ComparisonStyle.HashAndEquals =>
          elementsWithPath.to(LazyList).distinctBy(_._1)
        case DedupBehaviour.ComparisonStyle.HashOnly =>
          elementsWithPath.to(LazyList).distinctBy(_._1.hashCode)
      }
    )

  // TODO add type safety once we're on dotty, similar to gremlin-scala's as/label steps with typelevel append?
  override def path: Traversal[Seq[Any]] =
    new Traversal(elementsWithPath.map { case (a, path) =>
      (path :+ a).to(Seq)
    })

  override def repeat[B >: A](repeatTraversal: Traversal[A] => Traversal[B])
                    (implicit behaviourBuilder: RepeatBehaviour.Builder[B] => RepeatBehaviour.Builder[B] = RepeatBehaviour.noop[B] _)
    : Traversal[B] = {
    val behaviour = behaviourBuilder(new RepeatBehaviour.Builder[B]).build
    val _repeatTraversal = repeatTraversal.asInstanceOf[Traversal[B] => Traversal[B]] //this cast usually :tm: safe, because `B` is a supertype of `A`
    val repeat0: B => PathAwareTraversal[B] = PathAwareRepeatStep(_repeatTraversal, behaviour)
    new PathAwareTraversal(iterator.flatMap { a =>
      repeat0(a).elementsWithPath
    })
  }

}

object PathAwareTraversal {
  def empty[A]: PathAwareTraversal[A] =
    new PathAwareTraversal(Iterator.empty)

  def fromSingle[A](a: A): PathAwareTraversal[A] =
    new PathAwareTraversal(Iterator.single((a, Vector.empty)))

  def from[A](iter: IterableOnce[A]): PathAwareTraversal[A] =
    new PathAwareTraversal[A](iter.map(a => (a, Vector.empty)))
}