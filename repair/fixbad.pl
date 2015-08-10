#!/usr/bin/perl -w
use strict;

my %missing=();

open(IN,"good.tab")||die "Can't open good.tab\n";
<IN>;
while(my $line=<IN>){
  chomp($line);
  my ($fullname,$first,$last,$email,$home,$fax,$mobile,$pager,$street,$city,$state,$zip,$work,$company,$title,$notes,$timestamp,$dept)=split(/\t/,$line);
  my $val="$first\t$last\t$company\t$title";
  $missing{$fullname}=$val;
}
close(IN);

<STDIN>;
while(my $line=<STDIN>){
  chomp($line);
  my ($fullname,$first,$last,$email,$home,$fax,$mobile,$pager,$street,$city,$state,$zip,$work,$company,$title,$notes,$timestamp,$dept)=split(/\t/,$line);
  my $val = $missing{$fullname};
  if (defined($val)){
    ($first,$last,$company,$title)=split(/\t/,$val);
  }
  print "$fullname	$first	$last	$email	$home	$fax	$mobile	$pager	$street	$city	$state	$zip	$work	$company	$title	$notes	$timestamp	$dept\n";
}
