package com.eight87.tonearm.data.model

data class Album(
  val id: Long,
  val name: String,
  val artist: String?,
  val trackCount: Int,
  val year: Int?,
)
