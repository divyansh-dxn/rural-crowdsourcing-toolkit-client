package com.microsoft.research.karya.ui

sealed class Destination {
  object Splash : Destination()
  object AccessCodeFlow : Destination()
  object UserSelection : Destination()
  object LoginFlow : Destination()
  object Dashboard : Destination()
  object ProfileFragment : Destination()
}
